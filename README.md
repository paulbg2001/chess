# Chess LAN

Joc de șah în rețea locală pentru 2 jucători.

## Cerințe

- Java 17+
- Maven

## Pornire

### 1. Server (pe calculatorul gazdă)

```bash
cd chess-server
mvn spring-boot:run
```

Serverul pornește pe portul **8080**.

### 2. Client

```bash
cd chess-client
mvn javafx:run
```

La pornire, introdu:
- `localhost` - dacă ești pe același calculator cu serverul
- `192.168.x.x` - IP-ul calculatorului cu serverul (pentru LAN) afli cu ipconfig sau ifconfig pe mac

## Reguli

- Primul conectat = ALB
- Al doilea = NEGRU
- Captură rege = victorie
