#include <BLEDevice.h>
#include <BLEServer.h>
#include <BLEUtils.h>
#include <BLE2902.h>
#include <Wire.h>
//#include <Adafruit_GFX.h>
//#include <Adafruit_SSD1306.h>

#define SCREEN_WIDTH 128
#define SCREEN_HEIGHT 64

//Adafruit_SSD1306 display(SCREEN_WIDTH, SCREEN_HEIGHT, &Wire, -1);

// Android에서 사용할 UUID (예시 값, Android에서도 똑같이 사용해야 함)
#define SERVICE_UUID        "12345678-1234-5678-1234-56789abcdef0"
#define CHARACTERISTIC_UUID "abcdef12-3456-7890-abcd-ef1234567890"

BLEServer* pServer = nullptr;
BLECharacteristic* pCharacteristic = nullptr;
bool deviceConnected = false;

// 연결 상태 콜백입니다. BLE 연결 성사시와 연결 종료시 실행됩니다.
class MyServerCallbacks : public BLEServerCallbacks {
  void onConnect(BLEServer* pServer) {
    deviceConnected = true;
    Serial.println("BLE Connected");
  }

  void onDisconnect(BLEServer* pServer) {
    deviceConnected = false;
    Serial.println("BLE Disconnected");

    pServer->startAdvertising();
  }
};

// 수신 콜백입니다. 수신 데이터에 따른 처리는 여기서 하면 됩니다.
class MyCallbacks : public BLECharacteristicCallbacks {
  void onWrite(BLECharacteristic* pCharacteristic) {
    String value = pCharacteristic->getValue();

    if (value.length() > 0) {
      Serial.print("Received Data :  ");
      Serial.println(value);
      //수신된 데이터의 처리 및 작동이 여기서 정의가 되야 합니다.
      //display.clearDisplay();
      //display.setCursor(0, 0);
      //display.setTextSize(1);         // 글자 크기 (1 배율)
      //display.setTextColor(WHITE);    // 글자 색 (흰색)
      //display.println(value);         // 받은 문자열 출력
      //display.display();    

      // 받은 데이터를 그대로 다시 보내기 (에코 응답)
      //pCharacteristic->setValue(value);
      //pCharacteristic->notify(); // Android로 전송
    }
  }
};

void setup() {
  Serial.begin(115200);

  // SSD1306 디스플레이 제어를 위한 코드로, 실제 장치의 경우 필요 없을겁니다.
  // I2C 초기화 (ESP32 기본 SDA: GPIO21, SCL: GPIO22)
  //Wire.begin(21, 22);
  //if(!display.begin(SSD1306_SWITCHCAPVCC, 0x3C)) { 
  //  Serial.println(F("SSD1306 allocation failed"));
  //  for(;;); 
  //}
  // 화면 제어 초기화 코드입니다. 실제// 장치의 경우 필요 없을 겁니다.
  //display.clearDisplay();
  //display.setTextSize(1);
  //display.setTextColor(WHITE);
  //display.setCursor(0, 0);
  //display.println("Init done!");
  //display.display();


  BLEDevice::init("ESP32");
  pServer = BLEDevice::createServer();
  pServer->setCallbacks(new MyServerCallbacks());

  BLEService* pService = pServer->createService(SERVICE_UUID);

  pCharacteristic = pService->createCharacteristic(
                      CHARACTERISTIC_UUID,
                      BLECharacteristic::PROPERTY_READ |
                      BLECharacteristic::PROPERTY_WRITE |
                      BLECharacteristic::PROPERTY_NOTIFY
                    );

  pCharacteristic->setCallbacks(new MyCallbacks());
  pCharacteristic->addDescriptor(new BLE2902()); 
  pCharacteristic->setValue("ESP32 Ready");

  pService->start();
  pServer->getAdvertising()->start();

  Serial.println("BLE Started");
}

void loop() {
  // 필요 시 loop 내에서 BLE 상태 체크 가능
}
