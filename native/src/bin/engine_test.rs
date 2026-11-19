//! Host-side smoke test: runs the exact same engine flow as the Android IME,
//! through the real osmium-native wrapper (OsRiti), including candidate
//! reordering and Probhat AltGr keys.
//! Usage: cargo run --bin engine_test -- <path-to-riti-data-dir>

use osmium_native::keycode_for_char;
use osmium_native::OsRiti;

fn main() {
    let dir = std::env::args().nth(1).expect("usage: engine_test <data-dir>");
    let user_dir = "/tmp/osmium-test-user";
    std::fs::create_dir_all(user_dir).unwrap();

    // ---------------- Avro phonetic flow ----------------
    let riti = OsRiti::new(dir.clone(), user_dir.to_string(), "avro_phonetic".to_string());

    let text = std::env::args().nth(2)
        .unwrap_or_else(|| "ami bang banglay gan gai".to_string());
    println!("typing: {text:?}");
    for c in text.chars() {
        if c == ' ' {
            println!("  <space> -> commit candidate 0");
            riti.candidate_committed(0);
            continue;
        }
        let code = keycode_for_char(c.to_string());
        assert_ne!(code, 0, "no keycode for '{c}'");
        let s = riti.suggest_for_key(code, 0, 0);
        let preview: Vec<String> = s.candidates.iter().take(4).cloned().collect();
        println!(
            "  '{c}' -> aux={:?} sel={} cands={:?} orig={:?}",
            s.auxiliary, s.selection, preview, s.original_indices
        );
        assert!(
            s.candidates
                .first()
                .map(|c| c.chars().next().unwrap() <= '\u{09FF}')
                .unwrap_or(true),
            "first candidate must be Bengali-script, got {:?}",
            s.candidates.first()
        );
        // original_indices maps each display slot to a riti candidate and must
        // be a full permutation, otherwise a committed candidate maps to the
        // wrong word.
        assert_eq!(
            s.original_indices.len(),
            s.candidates.len(),
            "original_indices must cover every displayed candidate"
        );
        let mut sorted = s.original_indices.clone();
        sorted.sort();
        assert_eq!(
            sorted,
            (0..s.original_indices.len() as i32).collect::<Vec<_>>(),
            "original_indices must be a permutation of 0..len"
        );
        if let Some(&original) = s.original_indices.get(s.selection as usize) {
            assert!(
                (original as usize) < s.original_indices.len(),
                "selected candidate's original index must be in range"
            );
        }
    }

    // ---------------- Bengali orthography regressions ----------------
    // Common romanised words must be reachable in the candidate list. We check
    // presence rather than top-1 so dictionary updates don't create false alarms,
    // but a missing word would be a real transliteration regression.
    let orth = OsRiti::new(dir.clone(), user_dir.to_string(), "avro_phonetic".to_string());
    // A few words have two accepted spellings (য / য়); riti picks one, so list
    // both rather than encoding a dictionary preference as a "correctness" test.
    let cases: &[(&str, &[&str])] = &[
        ("ami", &["আমি"]),
        ("bangla", &["বাংলা"]),
        ("banglay", &["বাংলায়", "বাংলায়"]),
        ("gai", &["গাই"]),
    ];
    for &(roman, accepted) in cases {
        orth.finish_session();
        let mut state = None;
        for c in roman.chars() {
            let code = keycode_for_char(c.to_string());
            assert_ne!(code, 0, "no keycode for '{c}'");
            state = Some(orth.suggest_for_key(code, 0, 0));
        }
        let state = state.expect("non-empty romanised word");
        assert!(
            state.candidates.iter().any(|c| accepted.contains(&c.as_str())),
            "'{roman}' should offer one of {accepted:?}, got {:?}",
            state.candidates.iter().take(5).collect::<Vec<_>>()
        );
        orth.candidate_committed(0);
    }
    println!("orthography cases OK");

    // ---------------- National Jatiya fixed layout ----------------
    let jatiya_path = format!("{dir}/National_Jatiya.json");
    let jatiya = OsRiti::new(dir.clone(), user_dir.to_string(), jatiya_path);
    for (ch, expect) in [('k', 'ত'), ('a', 'ৃ'), ('D', 'ী')] {
        jatiya.finish_session();
        let code = keycode_for_char(ch.to_string());
        let s = jatiya.suggest_for_key(code, 0, 0);
        let got = s.candidates.first().cloned().unwrap_or_default();
        assert_eq!(
            got.chars().next(),
            Some(expect),
            "Jatiya '{ch}' should give '{expect}', got '{got}'"
        );
    }
    println!("jatiya layout OK");

    // ---------------- Probhat layout + AltGr layer ----------------
    let probhat_path = format!("{dir}/Probhat.json");
    let probhat = OsRiti::new(dir.clone(), user_dir.to_string(), probhat_path);

    // (key char, modifier, expected output) — MODIFIER_ALT_GR = 2.
    let altgr_cases = [
        ('a', 2, 'ঌ'),
        ('s', 2, 'ৡ'),
        ('d', 2, 'ৄ'),
        ('h', 2, 'ঽ'),
        ('0', 2, '৸'),
        ('.', 2, '়'),
    ];
    for (ch, modifier, expect) in altgr_cases {
        probhat.finish_session();
        let code = keycode_for_char(ch.to_string());
        let s = probhat.suggest_for_key(code, modifier, 0);
        let got = s.candidates.first().cloned().unwrap_or_default();
        assert_eq!(
            got.chars().next(),
            Some(expect),
            "Probhat AltGr '{ch}' (mod {modifier}) should give '{expect}', got '{got}'"
        );
        println!("probhat altgr '{ch}' -> {got:?} OK");
    }

    // Normal Probhat letters still work unmodified.
    for (ch, expect) in [('q', 'দ'), ('a', 'া'), ('.', '।')] {
        probhat.finish_session();
        let code = keycode_for_char(ch.to_string());
        let s = probhat.suggest_for_key(code, 0, 0);
        let got = s.candidates.first().cloned().unwrap_or_default();
        assert_eq!(
            got.chars().next(),
            Some(expect),
            "Probhat '{ch}' should give '{expect}', got '{got}'"
        );
        println!("probhat normal '{ch}' -> {got:?} OK");
    }

    // Backspace flow.
    let s = riti.backspace(false);
    println!("backspace -> {:?}", s.candidates.first());
    println!("ENGINE_TEST_OK");
}
