//! Host-side smoke test: runs the exact same engine flow as the Android IME.
//! Usage: cargo run --bin engine_test -- <path-to-riti-data-dir>

use osmium_native::keycode_for_char;
use riti::config::Config;
use riti::context::RitiContext;

fn main() {
    let dir = std::env::args().nth(1).expect("usage: engine_test <data-dir>");
    let user_dir = "/tmp/osmium-test-user";
    std::fs::create_dir_all(user_dir).unwrap();

    let mut config = Config::default();
    assert!(config.set_database_dir(&dir), "database dir missing: {dir}");
    config.set_user_dir(user_dir);
    assert!(config.set_layout_file_path("avro_phonetic"), "avro layout");
    config.set_phonetic_suggestion(true);
    config.set_suggestion_include_english(false);

    let ctx = RitiContext::new_with_config(&config);

    let text = "ami banglay gan gai";
    println!("typing: {text:?}");
    for c in text.chars() {
        if c == ' ' {
            println!("  <space> -> commit candidate 0");
            ctx.candidate_committed(0);
            continue;
        }
        let code = keycode_for_char(c.to_string());
        assert_ne!(code, 0, "no keycode for '{c}'");
        let s = ctx.get_suggestion_for_key(code as u16, 0, 0);
        match s {
            riti::suggestion::Suggestion::Full { auxiliary, suggestions, selection, .. } => {
                println!("  '{c}' -> aux={auxiliary:?} sel={selection} cands={:?}", 
                    &suggestions[..suggestions.len().min(4)]);
            }
            riti::suggestion::Suggestion::Single { suggestion, .. } => {
                println!("  '{c}' -> single={suggestion:?}");
            }
        }
    }

    // Backspace flow.
    let s = ctx.backspace_event(false);
    println!("backspace -> {:?}", s);
    println!("ENGINE_TEST_OK");
}
