use bluer::gatt::remote::{Characteristic, CharacteristicWriteRequest};
use bluer::gatt::WriteOp;
use bluer::Device;
use futures::{Stream, StreamExt};
use std::pin::Pin;
use std::str::FromStr;
use std::sync::Arc;
use tokio::sync::Mutex;
use uuid::Uuid;

const SERVICE_UUID: &str = "a667f940-6a50-49ac-9b75-2b9639564972";
const CHARACTERISTIC_UUID: &str = "69924d24-8e47-4d43-9e86-dde30201a474";

pub struct BluetoothConnection {
    device: Device,
    characteristic: Characteristic,
    notifications: Arc<Mutex<Pin<Box<dyn Stream<Item = Vec<u8>> + Send>>>>,
}

#[derive(Debug)]
pub enum Error {
    Bluetooth(bluer::Error),
    Timeout,
    StreamEnded,
    EmptyResponse,
    Utf8(std::string::FromUtf8Error),
}

impl std::fmt::Display for Error {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        match self {
            Error::Bluetooth(e) => write!(f, "Bluetooth error: {e}"),
            Error::Timeout => write!(f, "timed out waiting for response signal"),
            Error::StreamEnded => write!(f, "notification stream ended unexpectedly"),
            Error::EmptyResponse => write!(f, "empty response received"),
            Error::Utf8(e) => write!(f, "UTF-8 error: {e}"),
        }
    }
}

impl std::error::Error for Error {}

impl From<bluer::Error> for Error {
    fn from(e: bluer::Error) -> Self {
        Error::Bluetooth(e)
    }
}

impl BluetoothConnection {
    pub async fn connect(device_address: &str) -> Result<Self, Error> {
        let session = bluer::Session::new().await?;
        let adapter = session.default_adapter().await?;
        adapter.set_powered(true).await?;

        let addr = device_address
            .parse()
            .expect("invalid Bluetooth address format");
        let device = adapter.device(addr)?;
        device.connect().await?;

        let service_uuid = Uuid::from_str(SERVICE_UUID).expect("invalid service UUID");
        let char_uuid = Uuid::from_str(CHARACTERISTIC_UUID).expect("invalid characteristic UUID");

        let characteristic = {
            let mut found = None;
            for service in device.services().await? {
                if service.uuid().await? != service_uuid {
                    continue;
                }
                for char in service.characteristics().await? {
                    if char.uuid().await? == char_uuid {
                        found = Some(char);
                        break;
                    }
                }
                if found.is_some() {
                    break;
                }
            }
            found.expect("characteristic not found on device")
        };

        let notifications = Box::pin(characteristic.notify().await?)
            as Pin<Box<dyn Stream<Item = Vec<u8>> + Send>>;
        let notifications = Arc::new(Mutex::new(notifications));

        Ok(Self {
            device,
            characteristic,
            notifications,
        })
    }

    pub async fn send_message(&mut self, json: &str) -> Result<Vec<u8>, Error> {
        self.characteristic
            .write_ext(
                json.as_bytes(),
                &CharacteristicWriteRequest {
                    offset: 0,
                    op_type: WriteOp::Request,
                    ..Default::default()
                },
            )
            .await?;

        tokio::time::timeout(
            std::time::Duration::from_millis(5000),
            self.notifications.lock().await.next(),
        )
        .await
        .map_err(|_| Error::Timeout)?
        .ok_or(Error::StreamEnded)?;

        let response = self.characteristic.read().await?;

        if response.is_empty() {
            return Err(Error::EmptyResponse);
        }

        Ok(response)
    }

    pub async fn disconnect(&self) -> Result<(), Error> {
        self.device.disconnect().await?;
        Ok(())
    }
}
