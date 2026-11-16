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

    let text = "ami bang banglay gan gai";
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
            "  '{c}' -> aux={:?} sel={} cands={:?}",
            s.auxiliary, s.selection, preview
        );
        assert!(
            s.candidates
                .first()
                .map(|c| c.chars().next().unwrap() <= '\u{09FF}')
                .unwrap_or(true),
            "first candidate must be Bengali-script, got {:?}",
            s.candidates.first()
        );
    }

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
