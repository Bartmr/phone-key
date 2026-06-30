use serde::Deserialize;
use std::path::PathBuf;


fn config_path() -> PathBuf {
    let home = std::env::var("HOME")
        .or_else(|_| std::env::var("USERPROFILE"))
        .expect("HOME environment variable not set");
    PathBuf::from(home).join(".phone-key.json")
}

#[derive(Deserialize, Debug)]
pub struct Config {
    #[serde(default)]
    pub device_address: Option<String>,
}

impl Config {
    pub fn load() -> Result<Self, Box<dyn std::error::Error>> {
        let path = config_path();
        if !path.exists() {
            std::fs::write(&path, "{}")?;
        }
        let raw = std::fs::read_to_string(&path)?;
        let config: Config = serde_json::from_str(&raw)?;
        Ok(config)
    }
}


