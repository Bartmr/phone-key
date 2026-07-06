use base64::Engine;
use serde::Deserialize;
use ssh_agent_lib::agent::{listen, Session};
use ssh_agent_lib::error::AgentError;
use ssh_agent_lib::proto::{Identity, PublicCredential, SignRequest};
use ssh_agent_lib::ssh_key::{PublicKey, Signature};
use std::sync::Arc;
use tokio::net::UnixListener;
use tokio::sync::Mutex;
use phone_key_cli::{bluetooth, config};

const SOCKET_PATH: &str = "/tmp/phone-key-ssh-agent.sock";

#[derive(Deserialize)]

struct IdentityResponse {
    alias: String,
    #[serde(rename = "publicKeyBase64")]
    public_key_base64: String,
}

#[derive(Clone)]
struct AppSession {
    device_address: String,
    connection: Arc<Mutex<Option<bluetooth::BluetoothConnection>>>,
    identities: Vec<(PublicCredential, String)>,
}

impl AppSession {
    fn new(config: &config::Config) -> Self {
        let device_address = config
            .device_address
            .clone()
            .expect("device_address not set in ~/.phone-key.json");
        Self {
            device_address,
            connection: Arc::new(Mutex::new(None)),
            identities: Vec::new(),
        }
    }

    async fn send_message(&self, json: &str) -> Result<Vec<u8>, AgentError> {
        let mut guard = self.connection.lock().await;
        if guard.is_none() {
            *guard = Some(
                bluetooth::BluetoothConnection::connect(&self.device_address)
                    .await
                    .map_err(|e| AgentError::other(std::io::Error::new(std::io::ErrorKind::Other, e.to_string())))?,
            );
        }

        match guard
            .as_mut()
            .expect("connection just initialized")
            .send_message(json)
            .await
        {
            Ok(response) => Ok(response),
            Err(e) => {
                // Dispose broken connection so next call creates a fresh one
                *guard = None;
                eprintln!("[ssh-agent] Bluetooth connection error: {e}.");
                Err(AgentError::other(std::io::Error::new(
                    std::io::ErrorKind::Other,
                    e.to_string(),
                )))
            }
        }
    }
}

#[ssh_agent_lib::async_trait]
impl Session for AppSession {
    async fn request_identities(&mut self) -> Result<Vec<Identity>, AgentError> {
        let response = self
            .send_message(r#"{"type":"ssh-request-identities"}"#)
            .await?;

        let parsed: Vec<IdentityResponse> = serde_json::from_slice(&response).map_err(|e| {
            AgentError::other(std::io::Error::new(
                std::io::ErrorKind::Other,
                format!("failed to parse identities response: {e}"),
            ))
        })?;

        self.identities.clear();
        let mut identities = Vec::new();
        for item in parsed {
            let public_key_bytes = base64::engine::general_purpose::STANDARD
                .decode(&item.public_key_base64)
                .map_err(|e| {
                    AgentError::other(std::io::Error::new(
                        std::io::ErrorKind::Other,
                        format!("failed to base64-decode public key for '{}': {e}", item.alias),
                    ))
                })?;
            let public_key_str = String::from_utf8(public_key_bytes).map_err(|e| {
                AgentError::other(std::io::Error::new(
                    std::io::ErrorKind::Other,
                    format!("invalid UTF-8 in public key for '{}': {e}", item.alias),
                ))
            })?;
            eprintln!("{}", public_key_str);
            let pk = PublicKey::from_openssh(&public_key_str).map_err(|e| {
                AgentError::other(std::io::Error::new(
                    std::io::ErrorKind::Other,
                    format!("invalid public key for '{}': {e}", item.alias),
                ))
            })?;
            let credential = PublicCredential::Key(pk.key_data().clone());
            identities.push(Identity {
                credential: credential.clone(),
                comment: item.alias.clone(),
            });
            self.identities.push((credential, item.alias));
        }

        Ok(identities)
    }

    async fn sign(&mut self, request: SignRequest) -> Result<Signature, AgentError> {
        let alias = self
            .identities
            .iter()
            .find(|(cred, _)| cred == &request.credential)
            .map(|(_, alias)| alias.as_str())
            .ok_or_else(|| {
                AgentError::other(std::io::Error::new(
                    std::io::ErrorKind::NotFound,
                    "key not found in cached identities",
                ))
            })?;

        let data_b64 = base64::engine::general_purpose::STANDARD.encode(&request.data);
        let json = serde_json::json!({
            "type": "ssh-sign",
            "keyAlias": alias,
            "data": data_b64,
        });

        let response = self.send_message(&json.to_string()).await?;
        if response.is_empty() {
            return Err(AgentError::other(std::io::Error::new(
                std::io::ErrorKind::Other,
                "signing failed on device",
            )));
        }

        let algorithm = request.credential.key_data().algorithm();
        Signature::new(algorithm, response).map_err(|e| {
            AgentError::other(std::io::Error::new(
                std::io::ErrorKind::Other,
                format!("failed to construct signature: {e}"),
            ))
        })
    }
}

#[tokio::main]
async fn main() -> Result<(), Box<dyn std::error::Error>> {
    let config = config::Config::load()?;
    let session = AppSession::new(&config);

    let _ = std::fs::remove_file(SOCKET_PATH);

    let listener = UnixListener::bind(SOCKET_PATH)?;
    println!("SSH agent listening on {SOCKET_PATH}");
    println!("export SSH_AUTH_SOCK={SOCKET_PATH}");

    listen(listener, session).await?;

    Ok(())
}
