package phonekey

import (
	"encoding/json"
	"os"
	"path/filepath"
)

func configPath() string {
	home, err := os.UserHomeDir()
	if err != nil {
		panic("HOME environment variable not set: " + err.Error())
	}
	return filepath.Join(home, ".phone-key.json")
}

type Config struct {
	DeviceAddress string `json:"deviceAddress,omitempty"`
}

func LoadConfig() (*Config, error) {
	path := configPath()
	if _, err := os.Stat(path); os.IsNotExist(err) {
		if err := os.WriteFile(path, []byte("{}\n"), 0644); err != nil {
			return nil, err
		}
	}
	data, err := os.ReadFile(path)
	if err != nil {
		return nil, err
	}
	var cfg Config
	if err := json.Unmarshal(data, &cfg); err != nil {
		return nil, err
	}
	return &cfg, nil
}
