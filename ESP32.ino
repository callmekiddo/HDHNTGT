#include <WiFi.h>
#include <freertos/task.h>
#include <freertos/queue.h>
#include <freertos/timers.h>
#include <freertos/semphr.h>

#define WIFI_SSID "Mang"
#define WIFI_PASSWORD "cunam225"

#define BUZZER_PIN 2
#define SIGNAL_QUEUE_SIZE 10
#define SERVER_PORT 8088

#define PHONE_IP "192.168.224.136"
#define PHONE_PORT 8086

QueueHandle_t signalQueue;
TimerHandle_t buzzerTimer;
SemaphoreHandle_t sendSignalSemaphore;
TaskHandle_t buzzerTaskHandle;
TaskHandle_t signalTaskHandle;
TaskHandle_t sendToPhoneTaskHandle;

WiFiServer server(SERVER_PORT);
bool buzzerAlert = true;

void connectToWiFi() {
  WiFi.begin(WIFI_SSID, WIFI_PASSWORD);
  while (WiFi.status() != WL_CONNECTED) {
    delay(1000);
    Serial.println("Connecting to WiFi...");
  }
  Serial.println("Connected to WiFi");
  Serial.print("ESP32 IP: ");
  Serial.println(WiFi.localIP());
}

void buzzerOffCallback(TimerHandle_t xTimer) {
  digitalWrite(BUZZER_PIN, LOW);
}

void buzzerTask(void *pvParameters) {
  char signal;
  while (true) {
    if (xQueueReceive(signalQueue, &signal, portMAX_DELAY) == pdTRUE) {
      switch (signal) {
        case 'a':
          if (buzzerAlert) {
            Serial.println("unknown");
            digitalWrite(BUZZER_PIN, HIGH);
            xTimerStart(buzzerTimer, 0);
            xSemaphoreGive(sendSignalSemaphore);
          }
          break;
        case 'b':
          Serial.println("known");
          digitalWrite(BUZZER_PIN, LOW);
          xTimerStop(buzzerTimer, 0);
          break;
        case 'c':
          Serial.println("on");
          buzzerAlert = true;
          break;
        case 'd':
          Serial.println("off");
          buzzerAlert = false;
          digitalWrite(BUZZER_PIN, LOW);
          xTimerStop(buzzerTimer, 0);
          break;
      }
    }
  }
}

void receiveSignal(void *parameter) {
  while (true) {
    WiFiClient client = server.available();
    if (client && client.available()) {
      char signal = client.read();
      xQueueSend(signalQueue, &signal, portMAX_DELAY);
    }
  }
}

void sendToPhoneTask(void *pvParameters) {
  while (true) {
    if (xSemaphoreTake(sendSignalSemaphore, portMAX_DELAY) == pdTRUE) {
      WiFiClient client;
      if (client.connect(PHONE_IP, PHONE_PORT)) {
        client.write('a');
        client.stop();
      }
    }
    vTaskDelay(pdMS_TO_TICKS(10000));
  }
}

void setup() {
  Serial.begin(115200);

  pinMode(BUZZER_PIN, OUTPUT);
  digitalWrite(BUZZER_PIN, LOW);

  connectToWiFi();
  server.begin();

  signalQueue = xQueueCreate(SIGNAL_QUEUE_SIZE, sizeof(char));
  if (signalQueue == NULL) {
    Serial.println("Failed to create signal queue");
    while (1)
      ;
  }

  buzzerTimer = xTimerCreate("BuzzerTimer", pdMS_TO_TICKS(8000), pdFALSE, (void *)0, buzzerOffCallback);
  if (buzzerTimer == NULL) {
    Serial.println("Failed to create buzzer timer");
    while (1)
      ;
  }

  sendSignalSemaphore = xSemaphoreCreateBinary();
  if (sendSignalSemaphore == NULL) {
    Serial.println("Failed to create semaphore");
    while (1)
      ;
  }

  xTaskCreate(buzzerTask, "BuzzerTask", 8000, NULL, 1, &buzzerTaskHandle);
  xTaskCreate(receiveSignal, "SignalTask", 8000, NULL, 1, &signalTaskHandle);
  xTaskCreate(sendToPhoneTask, "SendToPhoneTask", 8000, NULL, 1, &sendToPhoneTaskHandle);
}

void loop() {
  // Main loop
}
