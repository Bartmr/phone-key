use base64::Engine;
use ssh_agent_lib::agent::{listen, Session};
use ssh_agent_lib::error::AgentError;
use ssh_agent_lib::proto::{Identity, PublicCredential, SignRequest};
use ssh_agent_lib::ssh_key::{Algorithm, PublicKey, Signature};
use tokio::net::UnixListener;
use phone_key_cli::{bluetooth, config};

const SOCKET_PATH: &str = "/tmp/phone-key-agent.sock";

#[derive(Clone)]
struct AppSession {
    connection: bluetooth::BluetoothConnection,
}

impl AppSession {
    async fn new(config: &config::Config) -> Result<Self, Box<dyn std::error::Error>> {
        let device_address = config
            .device_address
            .as_deref()
            .expect("device_address not set in ~/.phone-key.json");
        let connection = bluetooth::BluetoothConnection::connect(device_address).await?;
        Ok(Self { connection })
    }

    async fn send_message(&mut self, json: &str) -> Result<Vec<u8>, AgentError> {
        self.connection.send_message(json).await.map_err(|e| {
            AgentError::other(std::io::Error::new(std::io::ErrorKind::Other, e.to_string()))
        })
    }
}

#[ssh_agent_lib::async_trait]
impl Session for AppSession {
    async fn request_identities(&mut self) -> Result<Vec<Identity>, AgentError> {
        let response = self.send_message("{\"command\":\"get-public-key\"}").await?;
        let public_key_str =
            String::from_utf8(response).map_err(AgentError::other)?;

        let public_key =
            PublicKey::from_openssh(&public_key_str).map_err(AgentError::other)?;

        Ok(vec![Identity {
            credential: PublicCredential::Key(public_key.key_data().clone()),
            comment: "phone-key".into(),
        }])
    }

    async fn sign(&mut self, request: SignRequest) -> Result<Signature, AgentError> {
        let data_base64 = base64::engine::general_purpose::STANDARD.encode(&request.data);
        let command =
            serde_json::json!({"command": "sign", "data": data_base64}).to_string();

        let response = self.send_message(&command).await?;

        Signature::new(
            Algorithm::new("ecdsa-sha2-nistp256").map_err(AgentError::other)?,
            response,
        )
        .map_err(AgentError::other)
    }
}

#[tokio::main]
async fn main() -> Result<(), Box<dyn std::error::Error>> {
    let config = config::Config::load()?;
    let session = AppSession::new(&config).await?;

    let _ = std::fs::remove_file(SOCKET_PATH);

    let listener = UnixListener::bind(SOCKET_PATH)?;
    println!("SSH agent listening on {SOCKET_PATH}");
    println!("export SSH_AUTH_SOCK={SOCKET_PATH}");

    listen(listener, session).await?;

    Ok(())
}
