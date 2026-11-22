# 🏁 FLZ RACE

App Android que transforma o celular em um **cronômetro de desempenho automotivo**: mede o tempo de **0 a 100 km/h**, a **velocidade máxima**, a **velocidade média** e o **tempo de um trajeto A→B** criado no mapa, com **ranking online** entre os pilotos.

Projeto da disciplina de Desenvolvimento Android (B2), com tema livre e o uso obrigatório dos sensores do celular.
Visual inspirado em *Need for Speed* com *Tron*: fundo escuro, grade neon ciano e detalhes em laranja.

> ⚠️ **Aviso de segurança:** o app foi feito para ser usado **somente em locais fechados e autorizados** (autódromos, pistas, áreas privadas). Nunca use em vias públicas. O aviso aparece no cadastro e precisa ser aceito.

---

## ✨ Funcionalidades

| Funcionalidade | Descrição |
|---|---|
| **0 a 100 km/h** | Espera o carro parar, detecta a arrancada pelo acelerômetro e cronometra até 100 km/h. O instante exato é interpolado entre duas leituras do GPS. |
| **Velocidade livre** | Mede a velocidade máxima, a média e a distância percorrida. |
| **Rotas A→B** | Qualquer piloto cria uma rota tocando no mapa (ponto A e ponto B). Os outros correm na mesma rota e disputam o menor tempo. |
| **Ranking online** | Três rankings: 0 a 100, velocidade máxima e o ranking de cada rota. Cada piloto aparece uma vez, com a sua melhor marca. |
| **Foto do carro** | Tirada pela câmera dentro do app; vira a foto do perfil e aparece no ranking. |
| **Histórico e recordes** | As medições ficam salvas no aparelho, com indicação de novo recorde. |
| **Gráfico** | Gráfico velocidade × tempo na tela de resultado. |
| **Login com Google** | Perfil e recordes ficam na nuvem e voltam ao entrar em outro celular. Inclui logoff. |
| **Modo demonstração** | Simula uma arrancada realista para apresentar em sala, sem carro. Basta ligar o modo e dar uma sacudida leve no celular. Resultados de demonstração **não** contam para recordes nem para o ranking. |
| **Anti-trapaça** | A medição é invalidada se houver localização falsa (mock), sinal de GPS impreciso ou arrancada em descida. |

## 📡 Sensores e recursos do aparelho

| Sensor / recurso | Uso |
|---|---|
| **GPS** (`LocationManager`) | Velocidade, distância, posição na rota A→B |
| **Acelerômetro** (`TYPE_LINEAR_ACCELERATION`, ou acelerômetro com filtro passa-baixa) | Detecta o milissegundo da arrancada e mostra a aceleração em *g* |
| **Câmera** (CameraX) | Foto do carro para o perfil |
| **Vibração** | Avisa quando está pronto e quando a medição começa |
| **Notificação / serviço em primeiro plano** | A medição continua com a tela apagada |

## 🛠️ Tecnologias

- Java 11 · Android Studio · Gradle (Kotlin DSL) com *version catalog*
- minSdk 30 · targetSdk 37
- Material Components 3 (layouts em XML)
- CameraX
- Firebase Authentication (Google via Credential Manager) e Cloud Firestore
- osmdroid com o mapa escuro **CARTO Dark Matter** (dados © OpenStreetMap contributors, © CARTO)

## 🧱 Arquitetura

Segue o mesmo padrão dos trabalhos anteriores ([tres-sensores-android](https://github.com/Fabioluiiz22010/tres-sensores-android) e [multiclauton-android](https://github.com/Fabioluiiz22010/multiclauton-android)):

- um **serviço em primeiro plano** faz a medição e publica os dados num **estado estático compartilhado**;
- as telas apenas **observam** esse estado (interface `Ouvinte`);
- o acesso à nuvem fica concentrado numa única classe com **métodos estáticos e `Callback`**.

```
GPS ─┐                          ┌─> MedicaoActivity (ao vivo)
     ├─> MedicaoService ─> Estado ─┤
Acelerômetro (ArrancadaDetector)│   └─> ResultadoActivity ─> Nuvem ─> Firestore
SimuladorMedicao (demo) ─┘
```

| Classe | Responsabilidade |
|---|---|
| `MainActivity` | Abas Início, Acelerar, Ranking e Perfil; histórico; logoff; permissões |
| `BoasVindasActivity` | Login com Google, restauração do perfil e cadastro |
| `MedicaoActivity` | Velocímetro e dados ao vivo |
| `MedicaoService` | Lê GPS e acelerômetro, controla as fases da medição e as regras de validade |
| `ArrancadaDetector` | Detecta a arrancada pelo acelerômetro |
| `CalculadoraVelocidade` | Máxima, média, distância e 0 a 100 interpolado |
| `SimuladorMedicao` | Curva de velocidade do modo demonstração |
| `Estado` | Estado compartilhado, preferências, histórico e recordes |
| `ResultadoActivity` | Resultado, gráfico e envio ao ranking |
| `TelaRanking` | Aba de ranking (0 a 100, vel. máx. e rotas) |
| `RotasActivity` / `CriarRotaActivity` | Lista de rotas e criação de rota no mapa |
| `CameraActivity` / `FotoCarro` | Captura, confirmação e armazenamento da foto |
| `Nuvem` | Firebase: login, perfil, ranking e rotas |
| `VelocimetroView`, `GraficoVelocidadeView`, `GradeView` | Componentes visuais desenhados à mão |

## ☁️ Estrutura do Firestore

```
usuarios/{uid}               nome, carro, foto (Base64 256 px)
ranking_0a100/{uid}          valor (s), nome, carro, foto (96 px), data
ranking_vmax/{uid}           valor (km/h), nome, carro, foto, data
rotas/{id}                   nome, aLat, aLon, bLat, bLon, raioM, distanciaM, criador
rotas/{id}/ranking/{uid}     valor (s), nome, carro, foto, data
```

- A marca só é gravada quando é **melhor** que a anterior do piloto (transação).
- A posição é calculada com uma consulta de contagem (`count()`): quantos pilotos têm marca melhor, mais 1.
- As fotos vão como miniaturas em Base64 no próprio Firestore, sem precisar do Firebase Storage (que exige o plano pago).
- As regras de segurança estão em [`firestore.rules`](firestore.rules): os rankings e as rotas são públicos para leitura, mas cada piloto só escreve nos próprios documentos, o perfil completo só é lido pelo dono e as marcas precisam estar dentro de limites plausíveis (ex.: 0 a 100 entre 2 e 60 s).

## 🚀 Como rodar

Por segurança, **dois arquivos com chaves não estão no repositório**. É preciso criá-los:

1. **Firebase**
   - Crie um projeto no [Firebase Console](https://console.firebase.google.com/) e adicione um app Android com o pacote `com.example.flzrace`.
   - Cadastre a impressão digital SHA-1 da sua chave de depuração (`.\gradlew signingReport`).
   - Ative **Authentication → Google** e crie o **Cloud Firestore**.
   - Publique as regras do arquivo `firestore.rules`.
   - Baixe o `google-services.json` e coloque em `app/google-services.json`.

2. **Chave do mapa (CARTO)**
   - Crie uma chave gratuita em [carto.com](https://carto.com/).
   - Crie o arquivo `app/src/main/res/values/chaves.xml`:

   ```xml
   <resources>
       <string name="carto_api_key" translatable="false">SUA_CHAVE_AQUI</string>
   </resources>
   ```

3. Abra o projeto no Android Studio, sincronize o Gradle e rode em um celular com GPS.

### Permissões usadas

Localização precisa, câmera, notificações, serviço em primeiro plano (localização), vibração e internet.

## 🎓 Apresentação em sala

1. Na aba **Acelerar**, ligue o **Modo demonstração**.
2. Escolha **0 a 100** e dê uma sacudida leve no celular.
3. O app simula a arrancada, mostra o velocímetro ao vivo e abre o resultado com o gráfico.

## 👤 Autor

**Fabio Luiz Florêncio da Silva**
Análise e Desenvolvimento de Sistemas, UniSALESIANO Araçatuba
