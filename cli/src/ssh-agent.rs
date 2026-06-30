use base64::Engine;
use bluer::gatt::remote::{Characteristic, CharacteristicWriteRequest};
use bluer::gatt::WriteOp;
use futures::{Stream, StreamExt};
use std::pin::Pin;
use std::sync::Arc;
use tokio::sync::Mutex;
use ssh_agent_lib::agent::{listen, Session};
use ssh_agent_lib::error::AgentError;
use ssh_agent_lib::proto::{Identity, PublicCredential, SignRequest};
use ssh_agent_lib::ssh_key::{Algorithm, PublicKey, Signature};
use std::str::FromStr;
use tokio::net::UnixListener;
use uuid::Uuid;

mod config;
use config::Config;

const SERVICE_UUID: &str = "a667f940-6a50-49ac-9b75-2b9639564972";
const CHARACTERISTIC_UUID: &str = "69924d24-8e47-4d43-9e86-dde30201a474";
const SOCKET_PATH: &str = "/tmp/phone-key-agent.sock";

#[derive(Clone)]
struct PhoneKeySession {
    characteristic: Characteristic,
    notifications: Arc<Mutex<Pin<Box<dyn Stream<Item = Vec<u8>> + Send>>>>,
}

impl PhoneKeySession {
    async fn connect(config: &Config) -> Result<Self, Box<dyn std::error::Error>> {
        let device_address = config
            .device_address
            .as_deref()
            .expect("device_address not set in ~/.phone-key.json");

        let session = bluer::Session::new().await?;
        let adapter = session.default_adapter().await?;
        adapter.set_powered(true).await?;

        let addr = device_address
            .parse()
            .expect("invalid Bluetooth address format");
        let device = adapter.device(addr)?;
        device.connect().await?;

        let service_uuid =
            Uuid::from_str(SERVICE_UUID).expect("invalid service UUID");
        let char_uuid =
            Uuid::from_str(CHARACTERISTIC_UUID).expect("invalid characteristic UUID");

        let services = device.services().await?;
        let mut characteristic = None;
        for service in services {
            if service.uuid().await? != service_uuid {
                continue;
            }
            for char in service.characteristics().await? {
                if char.uuid().await? == char_uuid {
                    characteristic = Some(char);
                    break;
                }
            }
            if characteristic.is_some() {
                break;
            }
        }

        let characteristic =
            characteristic.expect("characteristic not found on device");

        let stream = Box::pin(characteristic.notify().await?)
            as Pin<Box<dyn Stream<Item = Vec<u8>> + Send>>;
        let notifications = Arc::new(Mutex::new(stream));

        Ok(Self { characteristic, notifications })
    }

    async fn send_message(&mut self, json: &str) -> Result<Vec<u8>, AgentError> {
        self.characteristic
            .write_ext(
                json.as_bytes(),
                &CharacteristicWriteRequest {
                    offset: 0,
                    op_type: WriteOp::Reliable,
                    ..Default::default()
                },
            )
            .await
            .map_err(AgentError::other)?;

        // Wait for the empty notification signal
        tokio::time::timeout(
            std::time::Duration::from_millis(5000),
            self.notifications.lock().await.next(),
        )
        .await
        .map_err(|_| {
            AgentError::other(std::io::Error::new(
                std::io::ErrorKind::TimedOut,
                "timed out waiting for response signal",
            ))
        })?
        .ok_or_else(|| {
            AgentError::other(std::io::Error::new(
                std::io::ErrorKind::Other,
                "notification stream ended unexpectedly",
            ))
        })?;

        // Read the full value via Read Long (BlueZ handles Read Blob automatically)
        let response = self
            .characteristic
            .read()
            .await
            .map_err(AgentError::other)?;

        if response.is_empty() {
            return Err(AgentError::other(std::io::Error::new(
                std::io::ErrorKind::Other,
                "empty response received",
            )));
        }

        Ok(response)
    }
}

#[ssh_agent_lib::async_trait]
impl Session for PhoneKeySession {
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
    let config = Config::load()?;
    let session = PhoneKeySession::connect(&config).await?;

    let _ = std::fs::remove_file(SOCKET_PATH);

    let listener = UnixListener::bind(SOCKET_PATH)?;
    println!("SSH agent listening on {SOCKET_PATH}");
    println!("export SSH_AUTH_SOCK={SOCKET_PATH}");

    listen(listener, session).await?;

    Ok(())
}
