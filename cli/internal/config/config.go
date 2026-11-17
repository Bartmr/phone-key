package config

import (
	"encoding/json"
	"fmt"
	"os"
	"path/filepath"
)

// DefaultPort is the phone server port used when the config file does not set one.
const DefaultPort = 8765

// MinPort and MaxPort bound the allowed phone server port.
const (
	MinPort = 1024
	MaxPort = 65535
)

type Config struct {
	Port int `json:"port"`
}

func Load() (*Config, error) {
	home, err := os.UserHomeDir()
	if err != nil {
		return nil, fmt.Errorf("cannot determine home directory: %w", err)
	}

	path := filepath.Join(home, ".phone-key.json")

	if _, err := os.Stat(path); os.IsNotExist(err) {
		return &Config{Port: DefaultPort}, nil
	}

	data, err := os.ReadFile(path)
	if err != nil {
		return nil, fmt.Errorf("failed to read config file at %s: %w", path, err)
	}

	cfg := Config{Port: DefaultPort}
	if err := json.Unmarshal(data, &cfg); err != nil {
		return nil, fmt.Errorf("failed to parse config file at %s: %w", path, err)
	}

	if cfg.Port < MinPort || cfg.Port > MaxPort {
		return nil, fmt.Errorf(
			"invalid port %d in config file at %s: must be between %d and %d",
			cfg.Port, path, MinPort, MaxPort,
		)
	}

	return &cfg, nil
}
