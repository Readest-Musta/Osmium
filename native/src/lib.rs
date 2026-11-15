//! osmium-native: UniFFI wrapper around the riti Bengali input engine.
//!
//! riti (https://github.com/OpenBangla/riti) does the heavy lifting —
//! Avro Phonetic, fixed layouts (Probhat etc.), dictionary suggestions.
//! This crate adapts it to a small, Kotlin-friendly API.

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
    /// Index of the currently selected candidate.
    pub selection: i32,
    /// True when there is nothing to compose.
    pub is_empty: bool,
}

impl From<Suggestion> for OsSuggestion {
    fn from(s: Suggestion) -> Self {
        match s {
            Suggestion::Full {
                auxiliary,
                suggestions,
                selection,
                ..
            } => OsSuggestion {
                auxiliary,
                candidates: suggestions,
                selection: selection as i32,
                is_empty: false,
            },
            Suggestion::Single { suggestion, .. } => {
                if suggestion.is_empty() {
                    OsSuggestion {
                        auxiliary: String::new(),
                        candidates: Vec::new(),
                        selection: 0,
                        is_empty: true,
                    }
                } else {
                    OsSuggestion {
                        auxiliary: String::new(),
                        candidates: vec![suggestion],
                        selection: 0,
                        is_empty: false,
                    }
                }
            }
        }
    }
}

/// A configured riti input context.
pub struct OsRiti {
    ctx: RitiContext,
}

impl OsRiti {
    pub fn new(database_dir: String, user_dir: String, layout: String) -> Self {
        let config = build_config(&database_dir, &user_dir, &layout);
        OsRiti {
            ctx: RitiContext::new_with_config(&config),
        }
    }

    /// Feed a key press (riti keycode) and get the updated suggestion state.
    pub fn suggest_for_key(&self, key: i32, modifier: i32, selection: i32) -> OsSuggestion {
        self.ctx
            .get_suggestion_for_key(key as u16, modifier as u8, selection as u8)
            .into()
    }

    /// A candidate from the suggestion list was committed.
    /// Ends the current input session.
    pub fn candidate_committed(&self, index: i32) {
        self.ctx.candidate_committed(index as usize);
    }

    /// A BackSpace event. With `ctrl` = true, deletes the whole word
    /// currently in composition.
    pub fn backspace(&self, ctrl: bool) -> OsSuggestion {
        self.ctx.backspace_event(ctrl).into()
    }

    /// End the current input session (dropping the composition).
    pub fn finish_session(&self) {
        self.ctx.finish_input_session();
    }

    pub fn is_session_active(&self) -> bool {
        self.ctx.ongoing_input_session()
    }

    /// Reconfigure the engine (e.g. layout changed) without dropping learned data.
    pub fn update_engine(&mut self, database_dir: String, user_dir: String, layout: String) {
        let config = build_config(&database_dir, &user_dir, &layout);
        self.ctx.update_engine(&config);
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
