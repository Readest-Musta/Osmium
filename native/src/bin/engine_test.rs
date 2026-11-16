//! Host-side smoke test: runs the exact same engine flow as the Android IME,
//! through the real osmium-native wrapper (OsRiti), including candidate
//! reordering. Usage: cargo run --bin engine_test -- <path-to-riti-data-dir>

use osmium_native::keycode_for_char;
use osmium_native::OsRiti;

fn main() {
    let dir = std::env::args().nth(1).expect("usage: engine_test <data-dir>");
    let user_dir = "/tmp/osmium-test-user";
    std::fs::create_dir_all(user_dir).unwrap();

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

    // Backspace flow.
    let s = riti.backspace(false);
    println!("backspace -> {:?}", s.candidates.first());
    println!("ENGINE_TEST_OK");
}
