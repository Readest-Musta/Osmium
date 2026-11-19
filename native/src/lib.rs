//! osmium-native: UniFFI wrapper around the riti Bengali input engine.
//!
//! riti (https://github.com/OpenBangla/riti) does the heavy lifting —
//! Avro Phonetic, fixed layouts (Probhat etc.), dictionary suggestions.
//! This crate adapts it to a small, Kotlin-friendly API.

pub mod glide;
pub use glide::*;

use riti::context::RitiContext;
use riti::keycodes::*;
use riti::suggestion::Suggestion;
use riti::config::Config;

/// A snapshot of riti's suggestion state, safe to hand across UniFFI.
#[derive(Clone, Debug, PartialEq)]
pub struct OsSuggestion {
    /// Text composed before the current word (previously committed words).
    pub auxiliary: String,
    /// Suggestion candidates for the current word.
    pub candidates: Vec<String>,
    /// Index of the currently selected candidate (display index, post-reorder).
    pub selection: i32,
    /// True when there is nothing to compose.
    pub is_empty: bool,
    /// display index -> riti's original candidate index. riti must be told the
    /// original index when a candidate is committed, never the display index.
    pub original_indices: Vec<i32>,
}

impl From<Suggestion> for OsSuggestion {
    fn from(s: Suggestion) -> Self {
        match s {
            Suggestion::Full {
                auxiliary,
                suggestions,
                selection,
                ..
            } => {
                // riti ranks emoji matches above dictionary/phonetic words
                // (fine for a desktop candidate window, wrong for a mobile
                // keyboard that commits the top suggestion on space).
                // Reorder: Bengali-script candidates first, everything else
                // after — stable, so riti's own ordering is kept per group.
                // `order[d] = o` means display position d holds riti's original
                // candidate o. That same permutation is exposed to the IME as
                // `original_indices` so a commit can be translated back.
                let order = bengali_first_order(&suggestions);
                let candidates: Vec<String> =
                    order.iter().map(|&i| suggestions[i].clone()).collect();
                let original_indices: Vec<i32> =
                    order.iter().map(|&i| i as i32).collect();
                // Remap riti's selection index into the reordered list.
                let selection = order
                    .iter()
                    .position(|&i| i == selection as usize)
                    .unwrap_or(0) as i32;
                OsSuggestion {
                    auxiliary,
                    candidates,
                    selection,
                    is_empty: false,
                    original_indices,
                }
            }
            Suggestion::Single { suggestion, .. } => {
                if suggestion.is_empty() {
                    OsSuggestion {
                        auxiliary: String::new(),
                        candidates: Vec::new(),
                        selection: 0,
                        is_empty: true,
                        original_indices: Vec::new(),
                    }
                } else {
                    OsSuggestion {
                        auxiliary: String::new(),
                        candidates: vec![suggestion],
                        selection: 0,
                        is_empty: false,
                        original_indices: vec![0],
                    }
                }
            }
        }
    }
}

/// Stable Bengali-first permutation of riti's suggestions.
/// `order[d] = o`: display position `d` holds riti's original candidate `o`.
fn bengali_first_order(suggestions: &[String]) -> Vec<usize> {
    let mut order: Vec<usize> = (0..suggestions.len()).collect();
    // slice::sort_by_key is stable, so riti's relative order is kept per group.
    order.sort_by_key(|&i| if starts_with_bengali(&suggestions[i]) { 0 } else { 1 });
    order
}

/// True if the first visible character (skipping joiners like ZWNJ/ZWJ)
/// is in the Bengali Unicode block (U+0980..U+09FF).
fn starts_with_bengali(text: &str) -> bool {
    text.chars()
        .find(|c| !matches!(c, '\u{200C}' | '\u{200D}'))
        .map(|c| ('\u{0980}'..='\u{09FF}').contains(&c))
        .unwrap_or(false)
}

/// A configured riti input context.
///
/// riti's internal state is not thread-safe (RefCell), so we serialize all
/// access with a Mutex — UniFFI objects must be Send + Sync. The Send/Sync
/// claims are sound because every access goes through the Mutex.
struct WrapSync<T>(T);
unsafe impl<T> Send for WrapSync<T> {}
unsafe impl<T> Sync for WrapSync<T> {}

pub struct OsRiti {
    ctx: std::sync::Mutex<WrapSync<RitiContext>>,
}

impl OsRiti {
    pub fn new(database_dir: String, user_dir: String, layout: String) -> Self {
        ensure_home(&user_dir);
        let config = build_config(&database_dir, &user_dir, &layout);
        OsRiti {
            ctx: std::sync::Mutex::new(WrapSync(RitiContext::new_with_config(&config))),
        }
    }

    /// Feed a key press (riti keycode) and get the updated suggestion state.
    pub fn suggest_for_key(&self, key: i32, modifier: i32, selection: i32) -> OsSuggestion {
        let ctx = self.ctx.lock().unwrap();
        ctx.0
            .get_suggestion_for_key(key as u16, modifier as u8, selection as u8)
            .into()
    }

    /// A candidate from the suggestion list was committed.
    /// Ends the current input session.
    pub fn candidate_committed(&self, index: i32) {
        let ctx = self.ctx.lock().unwrap();
        ctx.0.candidate_committed(index as usize);
    }

    /// A BackSpace event. With `ctrl` = true, deletes the whole word
    /// currently in composition.
    pub fn backspace(&self, ctrl: bool) -> OsSuggestion {
        let ctx = self.ctx.lock().unwrap();
        ctx.0.backspace_event(ctrl).into()
    }

    /// End the current input session (dropping the composition).
    pub fn finish_session(&self) {
        let ctx = self.ctx.lock().unwrap();
        ctx.0.finish_input_session();
    }

    pub fn is_session_active(&self) -> bool {
        let ctx = self.ctx.lock().unwrap();
        ctx.0.ongoing_input_session()
    }

    /// Reconfigure the engine (e.g. layout changed) without dropping learned data.
    pub fn update_engine(&self, database_dir: String, user_dir: String, layout: String) {
        ensure_home(&user_dir);
        let config = build_config(&database_dir, &user_dir, &layout);
        let mut ctx = self.ctx.lock().unwrap();
        ctx.0.update_engine(&config);
    }
}

/// riti's `Config::default()` resolves the fallback user dir from
/// XDG_DATA_HOME/HOME and unwraps — but Android IME processes have neither
/// env var set, which would panic. Seed HOME if missing.
fn ensure_home(user_dir: &str) {
    if std::env::var_os("HOME").is_none() {
        std::env::set_var("HOME", user_dir);
    }
}

fn build_config(database_dir: &str, user_dir: &str, layout: &str) -> Config {
    let mut config = Config::default();
    config.set_database_dir(database_dir);
    config.set_user_dir(user_dir);
    config.set_layout_file_path(layout);
    // Osmium defaults: dictionary suggestions on, English transliteration off.
    config.set_phonetic_suggestion(true);
    config.set_suggestion_include_english(false);
    config
}

/// Maps an ASCII character as typed on the physical layout to riti's keycode.
/// Shifted characters map to riti's shifted keycodes.
pub fn keycode_for_char(ch: String) -> i32 {
    let Some(c) = ch.chars().next() else {
        return 0;
    };
    let code: u16 = match c {
        // Alphabets
        'q' => VC_Q, 'w' => VC_W, 'e' => VC_E, 'r' => VC_R, 't' => VC_T,
        'y' => VC_Y, 'u' => VC_U, 'i' => VC_I, 'o' => VC_O, 'p' => VC_P,
        'a' => VC_A, 's' => VC_S, 'd' => VC_D, 'f' => VC_F, 'g' => VC_G,
        'h' => VC_H, 'j' => VC_J, 'k' => VC_K, 'l' => VC_L,
        'z' => VC_Z, 'x' => VC_X, 'c' => VC_C, 'v' => VC_V, 'b' => VC_B,
        'n' => VC_N, 'm' => VC_M,
        'Q' => VC_Q_SHIFT, 'W' => VC_W_SHIFT, 'E' => VC_E_SHIFT, 'R' => VC_R_SHIFT,
        'T' => VC_T_SHIFT, 'Y' => VC_Y_SHIFT, 'U' => VC_U_SHIFT, 'I' => VC_I_SHIFT,
        'O' => VC_O_SHIFT, 'P' => VC_P_SHIFT, 'A' => VC_A_SHIFT, 'S' => VC_S_SHIFT,
        'D' => VC_D_SHIFT, 'F' => VC_F_SHIFT, 'G' => VC_G_SHIFT, 'H' => VC_H_SHIFT,
        'J' => VC_J_SHIFT, 'K' => VC_K_SHIFT, 'L' => VC_L_SHIFT, 'Z' => VC_Z_SHIFT,
        'X' => VC_X_SHIFT, 'C' => VC_C_SHIFT, 'V' => VC_V_SHIFT, 'B' => VC_B_SHIFT,
        'N' => VC_N_SHIFT, 'M' => VC_M_SHIFT,
        // Numerals
        '1' => VC_1, '2' => VC_2, '3' => VC_3, '4' => VC_4, '5' => VC_5,
        '6' => VC_6, '7' => VC_7, '8' => VC_8, '9' => VC_9, '0' => VC_0,
        // Punctuation & symbols
        '`' => VC_GRAVE, '~' => VC_TILDE,
        '-' => VC_MINUS, '_' => VC_UNDERSCORE,
        '=' => VC_EQUALS, '+' => VC_PLUS,
        '[' => VC_BRACKET_LEFT, ']' => VC_BRACKET_RIGHT,
        '{' => VC_BRACE_LEFT, '}' => VC_BRACE_RIGHT,
        '\\' => VC_BACK_SLASH, '|' => VC_BAR,
        ';' => VC_SEMICOLON, ':' => VC_COLON,
        '\'' => VC_APOSTROPHE, '"' => VC_QUOTE,
        ',' => VC_COMMA, '<' => VC_LESS,
        '.' => VC_PERIOD, '>' => VC_GREATER,
        '/' => VC_SLASH, '?' => VC_QUESTION,
        '!' => VC_EXCLAIM, '@' => VC_AT, '#' => VC_HASH, '$' => VC_DOLLAR,
        '%' => VC_PERCENT, '^' => VC_CIRCUM, '&' => VC_AMPERSAND,
        '*' => VC_ASTERISK, '(' => VC_PAREN_LEFT, ')' => VC_PAREN_RIGHT,
        _ => return 0,
    };
    code as i32
}

pub fn modifier_shift() -> i32 {
    riti::context::MODIFIER_SHIFT as i32
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn bengali_first_order_is_stable_and_carries_original_indices() {
        let suggestions = vec![
            "ami".to_string(),    // 0: other
            "আমি".to_string(),    // 1: Bengali
            "bangla".to_string(), // 2: other
            "বাংলা".to_string(), // 3: Bengali
            "ami".to_string(),    // 4: other (stability check within group)
        ];
        let order = bengali_first_order(&suggestions);
        assert_eq!(order, vec![1, 3, 0, 2, 4]);

        // The same permutation is the display->original index mapping.
        let original_indices: Vec<i32> = order.iter().map(|&i| i as i32).collect();
        assert_eq!(original_indices, vec![1, 3, 0, 2, 4]);

        let candidates: Vec<&str> = order.iter().map(|&i| suggestions[i].as_str()).collect();
        assert_eq!(candidates, vec!["আমি", "বাংলা", "ami", "bangla", "ami"]);
    }

    #[test]
    fn selection_remaps_to_a_display_index_whose_original_is_riti_selection() {
        let suggestions = vec!["ami".to_string(), "আমি".to_string(), "bangla".to_string()];
        let order = bengali_first_order(&suggestions); // [1, 0, 2]
        let riti_selection = 0usize;
        let display = order
            .iter()
            .position(|&i| i == riti_selection)
            .expect("riti selection must be present");
        assert_eq!(display, 1);
        assert_eq!(order[display], riti_selection);
    }
}

// Include the UniFFI scaffolding generated from src/osmium.udl by build.rs.
uniffi::include_scaffolding!("osmium");
