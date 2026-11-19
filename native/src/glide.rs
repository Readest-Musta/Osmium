//! Glide decoding: shape-aware candidate generation with riti as lexical oracle.
//! The decoder owns a throwaway riti context and never commits, so it cannot
//! read or write the user's personal learning files.

use crate::keycode_for_char;
use riti::config::Config;
use riti::context::RitiContext;
use std::collections::HashSet;
use std::sync::Mutex;

#[derive(Clone, Debug)]
pub struct GlidePoint {
    pub x: f64,
    pub y: f64,
}

#[derive(Clone, Debug)]
pub struct GlideKey {
    pub code: String,
    pub cx: f64,
    pub cy: f64,
    pub half_w: f64,
    pub half_h: f64,
}

#[derive(Clone, Debug)]
pub struct WordWeight {
    pub word: String,
    pub weight: f64,
}

#[derive(Clone, Debug)]
pub struct GlideCandidate {
    pub word: String,
    pub score: f64,
    pub keys: Vec<String>,
}

const MAX_TRACE: usize = 64;
const MAX_SEQUENCES: usize = 16;
const MAX_CANDIDATES: usize = 5;
const FEATURE_COUNT: usize = 7;

/// Fixed, conservative base weights for the 7-feature scorer. The user vector
/// nudges these without any backpropagation; the 12 hidden units are a tiny
/// deterministic tanh layer, so no ML runtime or model file is needed.
const BASE_W: [f64; FEATURE_COUNT] = [1.0, 0.6, 0.3, 0.8, 0.4, 0.5, -0.2];
const HIDDEN: [[f64; FEATURE_COUNT]; 12] = [
    [1., 0., 0., 0., 0., 0., 0.], [0., 1., 0., 0., 0., 0., 0.],
    [0., 0., 1., 0., 0., 0., 0.], [0., 0., 0., 1., 0., 0., 0.],
    [0., 0., 0., 0., 1., 0., 0.], [0., 0., 0., 0., 0., 1., 0.],
    [0., 0., 0., 0., 0., 0., 1.], [1., 1., 0., 0., 0., 0., 0.],
    [0., 0., 1., 1., 0., 0., 0.], [0., 0., 0., 0., 1., 1., 0.],
    [1., 0., 1., 0., 1., 0., 1.], [0., 1., 0., 1., 0., 1., 0.],
];
const OUT_W: [f64; 12] = [
    0.25, 0.15, 0.10, 0.20, 0.10, 0.10, -0.05, 0.10, 0.08, 0.08, 0.12, 0.06,
];

pub struct OsGlideDecoder {
    ctx: Mutex<crate::WrapSync<RitiContext>>,
    dictionary: HashSet<String>,
}

impl OsGlideDecoder {
    pub fn new(database_dir: String, _user_dir: String, layout: String) -> Self {
        let mut dictionary = HashSet::new();
        if let Ok(raw) = std::fs::read_to_string(format!("{}/dictionary.json", database_dir)) {
            // Extract every quoted string and keep Bengali-script words. This is
            // deliberately dependency-free; the dictionary is ~4 MB of JSON.
            let mut current = String::new();
            let mut in_string = false;
            let mut escape = false;
            for ch in raw.chars() {
                if escape {
                    current.push(ch);
                    escape = false;
                    continue;
                }
                if ch == '\\' && in_string {
                    escape = true;
                    continue;
                }
                if ch == '"' {
                    if in_string {
                        if current.chars().next().map_or(false, |c| ('\u{0980}'..='\u{09FF}').contains(&c)) {
                            dictionary.insert(current.clone());
                        }
                        current.clear();
                    }
                    in_string = !in_string;
                } else if in_string {
                    current.push(ch);
                }
            }
        }

        let scratch = std::env::temp_dir()
            .join(format!("osmium-glide-{}", std::process::id()));
        let _ = std::fs::create_dir_all(&scratch);
        let mut config = Config::default();
        config.set_database_dir(&database_dir);
        config.set_user_dir(&scratch.to_string_lossy());
        config.set_layout_file_path(&layout);
        config.set_phonetic_suggestion(true);
        config.set_suggestion_include_english(false);
        OsGlideDecoder {
            ctx: Mutex::new(crate::WrapSync(RitiContext::new_with_config(&config))),
            dictionary,
        }
    }

    /// Nearest-key crossing sequence, deduplicated and bounded.
    fn key_sequence(trace: &[GlidePoint], keys: &[GlideKey]) -> Vec<String> {
        let mut out = Vec::new();
        for p in trace.iter().take(MAX_TRACE) {
            let mut best: Option<&GlideKey> = None;
            let mut best_dist = f64::MAX;
            for key in keys {
                let dx = (p.x - key.cx).abs() - key.half_w;
                let dy = (p.y - key.cy).abs() - key.half_h;
                let dist = dx.max(0.0).powi(2) + dy.max(0.0).powi(2);
                if dist < best_dist {
                    best_dist = dist;
                    best = Some(key);
                }
            }
            if let Some(k) = best {
                if out.last() != Some(&k.code) {
                    out.push(k.code.clone());
                }
            }
        }
        out
    }

    fn score(features: &[f64; FEATURE_COUNT], user_weights: &[f64]) -> f64 {
        let mut hidden = [0.0; 12];
        for (h, row) in HIDDEN.iter().enumerate() {
            let sum: f64 = (0..FEATURE_COUNT).map(|i| row[i] * features[i]).sum();
            hidden[h] = sum.tanh();
        }
        let mut total: f64 = OUT_W.iter().zip(hidden.iter()).map(|(w, h)| w * h).sum();
        for i in 0..FEATURE_COUNT {
            let extra = user_weights.get(i).copied().unwrap_or(0.0).clamp(-1.0, 1.0);
            total += (BASE_W[i] + extra) * features[i];
        }
        total
    }

    pub fn recognize(
        &self,
        trace: Vec<GlidePoint>,
        keys: Vec<GlideKey>,
        context: Vec<String>,
        prior: Vec<WordWeight>,
        user_weights: Vec<f64>,
    ) -> Vec<GlideCandidate> {
        let sequence = Self::key_sequence(&trace, &keys);
        if sequence.is_empty() || sequence.len() > 24 {
            return Vec::new();
        }
        let mut sequences: Vec<Vec<String>> = vec![sequence.clone()];
        // One-key deletions tolerate an extra detected crossing.
        for i in 0..sequence.len() {
            let mut variant = sequence.clone();
            variant.remove(i);
            if !variant.is_empty() && sequences.len() < MAX_SEQUENCES {
                sequences.push(variant);
            }
        }

        let mut found: Vec<(String, Vec<String>, usize)> = Vec::new();
        for seq in sequences {
            let raw: String = seq.concat();
            found.push((raw.clone(), seq.clone(), 0));
            let ctx = match self.ctx.lock() {
                Ok(c) => c,
                Err(_) => continue,
            };
            ctx.finish_input_session();
            let mut last = None;
            for code in &seq {
                let key = keycode_for_char(code.clone());
                if key == 0 {
                    last = None;
                    break;
                }
                last = Some(ctx.get_suggestion_for_key(key as u16, 0, 0));
            }
            if let Some(s) = last {
                let words: Vec<String> = match s {
                    riti::suggestion::Suggestion::Full { suggestions, .. } => suggestions,
                    riti::suggestion::Suggestion::Single { suggestion, .. } => {
                        if suggestion.is_empty() { Vec::new() } else { vec![suggestion] }
                    }
                };
                for (rank, word) in words.iter().take(3).enumerate() {
                    if !word.is_empty() {
                        found.push((word.clone(), seq.clone(), rank));
                    }
                }
            }
        }

        let prior_map: std::collections::HashMap<&str, f64> = prior.iter()
            .map(|p| (p.word.as_str(), p.weight)).collect();
        let mut results: Vec<GlideCandidate> = Vec::new();
        let mut seen = HashSet::new();
        for (word, seq, rank) in found {
            if !seen.insert(word.clone()) {
                continue;
            }
            let normalized = word.to_lowercase();
            let in_dict = self.dictionary.contains(&normalized) || self.dictionary.contains(&word);
            let user_weight = prior_map.get(normalized.as_str()).copied()
                .or_else(|| prior_map.get(word.as_str()).copied()).unwrap_or(0.0);
            let context_bonus = if context.iter().any(|w| prior_map.contains_key(w.as_str())) {
                0.3
            } else {
                0.0
            };
            let features = [
                0.8,                                  // shape fit (uniform until templates land)
                1.0 / (rank as f64 + 1.0),            // riti rank
                if in_dict { 0.7 } else { 0.1 },      // static frequency proxy
                context_bonus,                        // context match
                if in_dict { 0.0 } else { 0.6 },      // character-novelty fallback
                user_weight.clamp(0.0, 1.0),          // usage / recency
                -(word.chars().count() as f64) / 20.0, // length penalty
            ];
            results.push(GlideCandidate {
                word,
                score: Self::score(&features, &user_weights),
                keys: seq,
            });
        }
        results.sort_by(|a, b| b.score.partial_cmp(&a.score).unwrap_or(std::cmp::Ordering::Equal));
        results.truncate(MAX_CANDIDATES);
        results
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn key_sequence_dedups_repeats() {
        let keys = vec![
            GlideKey { code: "a".into(), cx: 10., cy: 10., half_w: 5., half_h: 5. },
            GlideKey { code: "m".into(), cx: 40., cy: 10., half_w: 5., half_h: 5. },
        ];
        let trace = vec![
            GlidePoint { x: 10., y: 10. }, GlidePoint { x: 11., y: 10. },
            GlidePoint { x: 40., y: 10. },
        ];
        assert_eq!(OsGlideDecoder::key_sequence(&trace, &keys), vec!["a", "m"]);
    }

    #[test]
    fn scoring_is_finite_and_bounded() {
        let f = [1.0; FEATURE_COUNT];
        let s = OsGlideDecoder::score(&f, &[0.0; FEATURE_COUNT]);
        assert!(s.is_finite());
        assert!(s.abs() < 20.0);
    }
}
