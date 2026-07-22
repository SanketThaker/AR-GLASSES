#include "time.h"
#include <BLE2902.h>
#include <BLEDevice.h>
#include <BLEServer.h>
#include <BLEUtils.h>
#include <U8g2lib.h>
#include <WiFi.h>
#include <WiFiMulti.h>
#include <Wire.h>

#define DEVICE_NAME "Sankets Glasses"
#define SERVICE_UUID "4fafc201-1fb5-459e-8fcc-c5c9c331914b"
#define CHAR_UUID_COMMAND "beb5483e-36e1-4688-b7f5-ea07361b26ae"
#define CHAR_UUID_STATUS "beb5483e-36e1-4688-b7f5-ea07361b26af"
#define BUTTON_PIN 4

unsigned long lastPressTime = 0;
unsigned long lastDebounceTime = 0;
const unsigned long pressDelay = 500;
const unsigned long debounceDelay = 50;
int pressCount = 0;
bool buttonState = HIGH;
bool stableState = HIGH;
bool lastStableState = HIGH;

WiFiMulti wifiMulti;
const char *ntpServer = "time.google.com";
const long gmtOffset_sec = 19800;
const int daylightOffset_sec = 0;

U8G2_SH1106_128X64_NONAME_F_HW_I2C u8g2(U8G2_MIRROR, U8X8_PIN_NONE);

BLECharacteristic *statusCharacteristic;
BLEServer *pServer;
bool deviceConnected = false;
bool oldDeviceConnected = false;
bool cameraActive = false;

enum UIMode { MODE_HOME, MODE_NOTIFICATION, MODE_CALL, MODE_NAV };
UIMode currentMode = MODE_HOME;
UIMode lastMode = MODE_HOME;
UIMode backgroundMode = MODE_HOME;

String navStreet   = "";
String navDistance = "";
String navArrow    = "";
String navThen     = "";
String navThenDir  = "";
String navETA      = "";
unsigned long navLastUpdate = 0;
const unsigned long navTimeout = 10000;

String receivedText = "";
struct LineRef { uint16_t start; uint8_t length; };
LineRef wrappedLines[150];
const int maxCharsPerLine = 22;
const int maxVisibleLines = 8;
const int lineHeight = 8;
int totalLines = 0;
int scrollIndex = 0;
unsigned long notificationStart = 0;
unsigned long scrollTimer = 0;
const int initialPause = 5000;
const int scrollDelay = 1500;
const int notificationTimeout = 30000;

String callerName = "";
bool callAccepted = false;
int brightnessValue = 200;
unsigned long tempTimer = 0;
unsigned long lastDrawTime = 0;

#define bt_width 8
#define bt_height 12
static const unsigned char bt_bits[] PROGMEM = {
    0x10,0x28,0x54,0x12,0x2a,0x44,0x44,0x2a,0x12,0x54,0x28,0x10};

#define call_width 24
#define call_height 24
static const unsigned char call_bits[] PROGMEM = {
    0x00,0x00,0x00,0x00,0x0f,0x00,0x80,0x1f,0x00,0xc0,0x3f,0x00,
    0xc0,0x3f,0x00,0xe0,0x37,0x00,0xe0,0x33,0x00,0xe0,0x13,0x00,
    0xe0,0x07,0x00,0xc0,0x07,0x00,0x80,0x0f,0x00,0x00,0x1f,0x00,
    0x00,0x3e,0x00,0x00,0x7c,0x00,0x00,0xf8,0x00,0x00,0xf0,0x01,
    0x00,0xe0,0x03,0x00,0xc0,0x07,0x00,0xc8,0x07,0x00,0xcc,0x07,
    0x00,0xec,0x07,0x00,0xfc,0x03,0x00,0xf8,0x01,0x00,0x00,0x00};

String arrowChar(const String &key) {
  if (key == "R")     return ">";
  if (key == "L")     return "<";
  if (key == "U")     return "^";
  if (key == "D")     return "v";
  if (key == "UR")    return ">>";
  if (key == "UL")    return "<<";
  if (key == "DR")    return ">v";
  if (key == "DL")    return "<v";
  if (key == "UTurn") return "(<)";
  return key;
}

String cleanString(String s) {
  s.replace("\xE2\x80\xA6", "...");
  s.replace("\xE2\x80\x9C", "\"");
  s.replace("\xE2\x80\x9D", "\"");
  s.replace("\xE2\x80\x98", "'");
  s.replace("\xE2\x80\x99", "'");
  String out = "";
  out.reserve(s.length());
  for (int i = 0; i < (int)s.length(); i++) {
    unsigned char c = (unsigned char)s.charAt(i);
    if (c >= 0x20 && c <= 0x7E) out += (char)c;
    else if (c == '\n')          out += '\n';
  }
  out.trim();
  return out;
}

void singlePress() {
  currentMode = MODE_HOME; backgroundMode = MODE_HOME;
  receivedText = ""; callAccepted = false;
  navStreet = navDistance = navArrow = navThen = navThenDir = navETA = "";
  Serial.println("Single -> Home");
}
void doublePress() {
  if (currentMode == MODE_CALL && !callAccepted) {
    if (deviceConnected) { statusCharacteristic->setValue("CALL:ACCEPT"); statusCharacteristic->notify(); }
    callAccepted = true;
  }
}
void triplePress() { receivedText = "SOS TRIGGERED"; wrapText(receivedText); }

String getDateString() {
  struct tm t; if (!getLocalTime(&t)) return "Syncing..";
  char buf[20]; strftime(buf, sizeof(buf), "%a, %b %d", &t); return String(buf);
}
String getTimeString() {
  struct tm t; if (!getLocalTime(&t)) return "--:--";
  char buf[20]; strftime(buf, sizeof(buf), "%I:%M %p", &t); return String(buf);
}
float getTemperature() { return temperatureRead(); }
void sendTemperature() {
  if (!deviceConnected) return;
  String d = "TEMP:" + String(getTemperature(), 1);
  statusCharacteristic->setValue(d.c_str()); statusCharacteristic->notify();
}

void drawNav() {
  u8g2.setFont(u8g2_font_5x8_tf);
  String street = navStreet;
  if (street.length() > 21) street = street.substring(0, 20) + ".";
  u8g2.drawStr(2, 8, street.c_str());

  u8g2.drawHLine(0, 10, 128);

  u8g2.setFont(u8g2_font_10x20_tf);
  String arrow = arrowChar(navArrow);
  int arrowW = u8g2.getStrWidth(arrow.c_str());
  u8g2.drawStr((128 - arrowW) / 2, 32, arrow.c_str());

  u8g2.setFont(u8g2_font_6x12_tf);
  String dist = navDistance;
  int distW = u8g2.getStrWidth(dist.c_str());
  u8g2.drawStr((128 - distW) / 2, 45, dist.c_str());

  u8g2.drawHLine(0, 47, 128);

  u8g2.setFont(u8g2_font_5x8_tf);

  // Bottom-left: "After: 150m >" when real next-turn known,
  // "Rem: 603m" when only remaining route distance available,
  // nothing when both are empty.
  String thenPart;
  if (navThen.length() > 0 && navThenDir.length() > 0) {
    thenPart = "After:" + navThen + " " + arrowChar(navThenDir);
  } else if (navThen.length() > 0) {
    thenPart = "Rem:" + navThen;
  } else {
    thenPart = "";
  }
  u8g2.drawStr(2, 55, thenPart.c_str());

  // ETA — right edge at 118
  if (navETA.length() > 0) {
    int etaW = u8g2.getStrWidth(navETA.c_str());
    u8g2.drawStr(118 - etaW, 55, navETA.c_str());
  }

  // Clock — right edge at 118
  String timeStr = getTimeString();
  int timeW = u8g2.getStrWidth(timeStr.c_str());
  u8g2.drawStr(118 - timeW, 63, timeStr.c_str());
}

void drawDashboard() {
  u8g2.setFont(u8g2_font_5x8_tf);
  String dateStr = getDateString();
  u8g2.drawStr((128 - u8g2.getStrWidth(dateStr.c_str())) / 2, 8, dateStr.c_str());
  u8g2.setFont(u8g2_font_10x20_tf);
  String timeStr = getTimeString();
  u8g2.drawStr((128 - u8g2.getStrWidth(timeStr.c_str())) / 2, 30, timeStr.c_str());
  u8g2.setFont(u8g2_font_5x8_tf);
  String tempStr = "Temp: " + String(getTemperature(), 1);
  u8g2.drawStr(5, 48, tempStr.c_str());
  int tempX = u8g2.getStrWidth(tempStr.c_str());
  u8g2.drawGlyph(tempX, 48, 0xB0);
  u8g2.drawStr(tempX + 5, 48, "C");
  if (deviceConnected) {
    u8g2.drawXBM(2, 52, bt_width, bt_height, bt_bits);
    u8g2.drawStr(10, 62, "CONNECTED");
    if (cameraActive) u8g2.drawStr(100, 62, "CAM");
  } else {
    u8g2.drawStr(2, 62, "BLE DISCONNECTED");
  }
}

void drawCall() {
  u8g2.drawXBM(52, 0, call_width, call_height, call_bits);
  u8g2.setFont(u8g2_font_6x12_tf);
  String label = callAccepted ? "On Call" : "Incoming Call";
  u8g2.drawStr((128 - u8g2.getStrWidth(label.c_str())) / 2, 34, label.c_str());
  String name = callerName;
  if (name.length() > 14) name = name.substring(0, 13) + ".";
  u8g2.drawStr((128 - u8g2.getStrWidth(name.c_str())) / 2, 50, name.c_str());
  if (!callAccepted) {
    u8g2.setFont(u8g2_font_5x8_tr);
    String hint = "[dbl=accept]";
    u8g2.drawStr((128 - u8g2.getStrWidth(hint.c_str())) / 2, 62, hint.c_str());
  }
}

void wrapText(const String &text) {
  totalLines = 0; scrollIndex = 0;
  int startIndex = 0;
  while (startIndex < (int)text.length() && totalLines < 150) {
    int nl = text.indexOf('\n', startIndex);
    int seg = (nl == -1) ? text.length() : nl;
    int si = startIndex;
    while (si < seg && totalLines < 150) {
      int end = si + maxCharsPerLine;
      if (end >= seg) { wrappedLines[totalLines++] = {(uint16_t)si, (uint8_t)(seg-si)}; break; }
      int sp = -1;
      for (int i = end; i > si; i--) { if (text.charAt(i)==' '){sp=i;break;} }
      if (sp==-1) { wrappedLines[totalLines++]={(uint16_t)si,(uint8_t)(end-si)}; si=end; }
      else        { wrappedLines[totalLines++]={(uint16_t)si,(uint8_t)(sp-si)};  si=sp+1; }
    }
    startIndex = (nl == -1) ? text.length() : nl + 1;
  }
  notificationStart = millis(); scrollTimer = millis();
  currentMode = MODE_NOTIFICATION;
}

void drawNotification() {
  u8g2.setFont(u8g2_font_5x8_tr);
  char lineBuf[32];
  for (int i = 0; i < maxVisibleLines; i++) {
    int idx = scrollIndex + i;
    if (idx < totalLines) {
      LineRef &r = wrappedLines[idx];
      int len = min((int)r.length, 31);
      for (int c = 0; c < len; c++) lineBuf[c] = receivedText.charAt(r.start + c);
      lineBuf[len] = '\0';
      u8g2.drawStr(2, (i+1)*lineHeight, lineBuf);
    }
  }
  if (millis()-notificationStart > initialPause && totalLines > maxVisibleLines)
    if (millis()-scrollTimer > scrollDelay) { if(++scrollIndex > totalLines-maxVisibleLines) scrollIndex=0; scrollTimer=millis(); }
  if (millis()-notificationStart > notificationTimeout) { receivedText=""; currentMode=backgroundMode; }
}

class MyServerCallbacks : public BLEServerCallbacks {
  void onConnect(BLEServer *p)    { deviceConnected = true; }
  void onDisconnect(BLEServer *p) {
    deviceConnected = false; currentMode = backgroundMode = MODE_HOME;
    p->startAdvertising(); Serial.println("BLE disconnected");
  }
};

class CommandCallbacks : public BLECharacteristicCallbacks {
  void onWrite(BLECharacteristic *pChar) {
    String command = cleanString(pChar->getValue().c_str());
    if (command.length() == 0) return;
    Serial.println("CMD: " + command);

    if (command.startsWith("BRIGHT:")) {
      brightnessValue = constrain(command.substring(7).toInt(), 0, 255);
      u8g2.setContrast(brightnessValue); return;
    }
    if (command == "CAMERA_ON")  { cameraActive = true;  return; }
    if (command == "CAMERA_OFF") { cameraActive = false; return; }
    if (command.startsWith("TEMP:")) return;

    if (command.startsWith("TIME:")) {
      String ts = command.substring(5);
      struct tm tm; time_t t = time(NULL); localtime_r(&t, &tm);
      tm.tm_hour = ts.substring(0,2).toInt();
      tm.tm_min  = ts.substring(3,5).toInt();
      tm.tm_sec  = 0;
      if (ts.length() >= 14) {
        tm.tm_mday = ts.substring(6,8).toInt();
        tm.tm_mon  = ts.substring(9,11).toInt()-1;
        tm.tm_year = ts.substring(12,14).toInt()+100;
      }
      struct timeval tv = { mktime(&tm), 0 };
      settimeofday(&tv, NULL); return;
    }

    if (command.startsWith("CALL:")) {
      String info = command.substring(5);
      if (info == "END" || info == "STOP") {
        callAccepted = false; currentMode = backgroundMode;
        if (currentMode == MODE_CALL) currentMode = MODE_HOME;
      } else {
        callerName = cleanString(info); callAccepted = false;
        if (currentMode != MODE_CALL) backgroundMode = currentMode;
        currentMode = MODE_CALL; notificationStart = millis();
      }
      return;
    }

    if (command.startsWith("NAV:")) {
      String navData = command.substring(4);
      navData.trim();
      if (navData == "END" || navData == "STOP") {
        currentMode = backgroundMode;
        if (currentMode == MODE_NAV) currentMode = MODE_HOME;
        navStreet=navDistance=navArrow=navThen=navThenDir=navETA="";
        return;
      }
      int p0=navData.indexOf('|');
      int p1=(p0>=0)?navData.indexOf('|',p0+1):-1;
      int p2=(p1>=0)?navData.indexOf('|',p1+1):-1;
      int p3=(p2>=0)?navData.indexOf('|',p2+1):-1;
      int p4=(p3>=0)?navData.indexOf('|',p3+1):-1;

      navStreet   = (p0>=0) ? navData.substring(0,p0)    : navData;
      navDistance = (p1>=0) ? navData.substring(p0+1,p1) : "";
      navArrow    = (p2>=0) ? navData.substring(p1+1,p2) : "";
      navThen     = (p3>=0) ? navData.substring(p2+1,p3) : "";
      navThenDir  = (p4>=0) ? navData.substring(p3+1,p4) : (p3>=0?navData.substring(p3+1):"");
      navETA      = (p4>=0) ? navData.substring(p4+1)    : "";

      navStreet.trim(); navDistance.trim(); navArrow.trim();
      navThen.trim(); navThenDir.trim(); navETA.trim();
      navETA = cleanString(navETA);

      navLastUpdate = millis();
      if (currentMode != MODE_NAV) backgroundMode = currentMode;
      currentMode = MODE_NAV;

      Serial.println("Nav: ["+navStreet+"] ["+navDistance+"] ["+navArrow+"] ETA:["+navETA+"]");
      return;
    }

    if (currentMode != MODE_NOTIFICATION) backgroundMode = currentMode;
    receivedText = command; wrapText(receivedText);
    notificationStart = millis(); currentMode = MODE_NOTIFICATION;
  }
};

void setup() {
  Serial.begin(115200);
  Wire.begin(17, 18);
  pinMode(BUTTON_PIN, INPUT_PULLUP);
  u8g2.begin(); u8g2.setFlipMode(0); u8g2.setContrast(brightnessValue);

  auto showMsg = [](const char *msg) {
    u8g2.clearBuffer(); u8g2.setFont(u8g2_font_profont12_tf);
    u8g2.drawStr((128-u8g2.getStrWidth(msg))/2, 35, msg); u8g2.sendBuffer();
  };

  showMsg("Connecting WiFi...");
  wifiMulti.addAP("Sanket's OnePlus Nord CE5","vnxj2116");
  wifiMulti.addAP("seslab3deg","$ES@lab123#");
  wifiMulti.addAP("S23","123qwerty");
  wifiMulti.addAP("JAI MAHAKAL","KAILASH$315");
  int att=0; while(wifiMulti.run()!=WL_CONNECTED&&att++<15) delay(500);

  if (WiFi.status()==WL_CONNECTED) {
    showMsg("Syncing Time...");
    configTime(gmtOffset_sec, daylightOffset_sec, ntpServer);
    struct tm ti; getLocalTime(&ti, 5000);
    WiFi.disconnect(true); WiFi.mode(WIFI_OFF);
  }

  showMsg("Starting BLE...");
  BLEDevice::init(DEVICE_NAME); BLEDevice::setMTU(200);
  pServer = BLEDevice::createServer();
  pServer->setCallbacks(new MyServerCallbacks());
  BLEService *pSvc = pServer->createService(SERVICE_UUID);
  BLECharacteristic *cmdChar = pSvc->createCharacteristic(CHAR_UUID_COMMAND, BLECharacteristic::PROPERTY_WRITE);
  cmdChar->setCallbacks(new CommandCallbacks());
  statusCharacteristic = pSvc->createCharacteristic(CHAR_UUID_STATUS,
      BLECharacteristic::PROPERTY_NOTIFY|BLECharacteristic::PROPERTY_READ);
  statusCharacteristic->addDescriptor(new BLE2902());
  pSvc->start();
  BLEAdvertising *adv = BLEDevice::getAdvertising();
  adv->addServiceUUID(SERVICE_UUID); adv->setAppearance(320);
  adv->setScanResponse(true); adv->setMinPreferred(0x20); adv->setMaxPreferred(0x40); adv->start();
}

void loop() {
  static unsigned long lastDraw = 0;
  unsigned long now = millis();

  bool reading = digitalRead(BUTTON_PIN);
  if (reading != buttonState) lastDebounceTime = now;
  if (now-lastDebounceTime > debounceDelay) {
    stableState = reading;
    if (lastStableState==HIGH && stableState==LOW) { pressCount++; lastPressTime=now; }
  }
  buttonState=reading; lastStableState=stableState;

  if (pressCount>0 && now-lastPressTime>pressDelay) {
    if(pressCount==1) singlePress(); else if(pressCount==2) doublePress(); else triplePress();
    pressCount=0;
  }

  if (currentMode==MODE_NAV && navLastUpdate>0 && now-navLastUpdate>navTimeout) {
    currentMode=backgroundMode; if(currentMode==MODE_NAV) currentMode=MODE_HOME;
    navStreet=navDistance=navArrow=navThen=navThenDir=navETA="";
  }

  if (now-lastDraw>=100) {
    lastDraw=now; u8g2.clearBuffer();
    if      (currentMode==MODE_CALL)         drawCall();
    else if (currentMode==MODE_NAV)          drawNav();
    else if (currentMode==MODE_NOTIFICATION) drawNotification();
    else                                     drawDashboard();
    u8g2.sendBuffer();
  }
  if (now-tempTimer>3000) { sendTemperature(); tempTimer=now; }
}
