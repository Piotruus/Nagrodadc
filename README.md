# NagrodySocial — Paper 1.21.4

Plugin otwiera GUI pod:
- /nagroda
- /dc
- /discord

Wszystkie trzy komendy są aliasami.

## Zasada czasu
Jest JEDEN wspólny licznik czasu gry: 3600 sekund.
Po osiągnięciu 1 godziny gracz może odebrać każdą z nagród, ale każdą tylko raz.
Social można połączyć wcześniej.

## Nagroda
Domyślna komenda:
`getcase give %player% firebox 2`

Nazwa w GUI:
`&4&lFire&6&lbox &e&l1x`

## Discord
Plugin ma przygotowaną integrację z botem JDA:
1. W config.yml ustaw bot-token i guild-id.
2. W GUI kliknij Discord.
3. Plugin generuje kod.
4. Gracz wpisuje na Discordzie `/nagroda KOD`.
5. Bot wiąże konto Discord z nickiem Minecraft.
6. Status zmienia się na Połączono.

## YouTube/TikTok — ważne
Nie da się wiarygodnie wykryć konkretnej subskrypcji/followa tylko na podstawie publicznego URL kanału.
Do prawdziwej automatycznej weryfikacji potrzebne są OAuth/API oraz zgoda użytkownika.
Config zawiera miejsce na dane OAuth, ale moduł OAuth wymaga skonfigurowania prawdziwych danych deweloperskich platformy.

## Budowanie
Wymagane Java 21 + Maven:
`mvn clean package`

Gotowy jar pojawi się w `target/`.
