# API de Integração Laboratorial - Manual Completo

## 1. Visão Geral

Esta aplicação é uma solução de middleware de nível empresarial desenvolvida em Java com Spring Boot. Sua função é servir como um hub central de comunicação, traduzindo e roteando informações entre um Sistema de Informação Laboratorial (LIS) e múltiplos equipamentos de análises clínicas.

### 1.1. Principais Funcionalidades
*   **API RESTful para LIS:** Oferece endpoints padronizados para o LIS gerenciar o ciclo de vida das ordens de exames.
*   **Documentação Interativa (Swagger):** Disponibiliza uma interface web (`Swagger UI`) para explorar, entender e testar todos os endpoints da API em tempo real.
*   **Conectividade Multi-protocolo:** Suporta os principais padrões da indústria:
    *   **ASTM (LIS1-A / LIS2-A):** Para comunicação serial.
    *   **HL7 (v2.5):** Para comunicação via TCP/IP, incluindo o encapsulamento MLLP.
    *   **Protocolos Proprietários:** Estrutura extensível para lidar com protocolos específicos, como o Roche HIF.
*   **Comunicação Bidirecional:**
    *   **Modo Servidor (Passivo):** Ouve passivamente por resultados enviados pelos equipamentos.
    *   **Modo Host Query:** Responde ativamente a requisições de ordens de trabalho iniciadas pelos equipamentos.
*   **Comunicação Mestre-Escravo:**
    *   **Modo Mestre (Ativo):** Inicia a comunicação e solicita ativamente os resultados de equipamentos que operam como "escravos".
*   **Auditoria e Rastreabilidade:** Todas as mensagens brutas trocadas com os equipamentos são salvas em arquivos de log para fins de auditoria, depuração e conformidade.

## 2. Arquitetura e Fluxo de Dados

O sistema opera em dois fluxos principais:

1.  **Fluxo LIS -> Equipamento (Host Query):**
    *   `LIS` -> `(POST /api/v1/orders)` -> `API` (Salva a ordem como "PENDING").
    *   `Equipamento` -> `(Envia Query)` -> `API` (Busca a ordem "PENDING").
    *   `API` -> `(Envia Resposta com a Ordem)` -> `Equipamento` (Processa o exame).

2.  **Fluxo Equipamento -> LIS (Envio de Resultado):**
    *   `Equipamento` -> `(Envia Resultado)` -> `API` (Faz o parse da mensagem).
    *   `API` (Atualiza a ordem para "COMPLETED" com o resultado).
    *   `LIS` -> `(GET /api/v1/orders)` -> `API` (Retorna a ordem "COMPLETED").

## 3. Guia de Instalação e Configuração

### 3.1. Pré-requisitos
*   **Java JDK 17 ou superior:** [Link para download](https://www.oracle.com/java/technologies/downloads/)
*   **Apache Maven 3.6 ou superior:** [Link para download](https://maven.apache.org/download.cgi)
*   **Git:** Para clonar o repositório.

### 3.2. Configuração do Projeto
1.  **Clonar o Repositório:**
    ```bash
    git clone [URL_DO_SEU_REPOSITORIO]
    cd lab-integration-api
    ```
2.  **Configurar Equipamentos (`application.yml`):**
    *   Abra o arquivo `src/main/resources/application.yml`.
    *   Este arquivo é o coração da configuração. Para cada equipamento a ser usado, ajuste o bloco correspondente:

    ```yaml
    equipments:
      devices:
        # Exemplo 1: Equipamento Serial que faz Host Query
        cell-dyn-ruby:
          name: "CELL-DYN RUBY"
          enabled: true               # true para ativar, false para desativar
          protocol: ASTM
          communication:
            type: SERIAL
            port-name: COM5           # IMPORTANTE: Mapear para a porta COM correta no servidor
            baud-rate: 9600           # Ajustar conforme manual do equipamento
            data-bits: 8
            stop-bits: 1
            parity: NONE

        # Exemplo 2: Equipamento TCP/IP que envia resultados
        maglumi-x3:
          name: "MAGLUMI X3"
          enabled: true
          protocol: HL7
          communication:
            type: TCP
            port: 5001                # IMPORTANTE: Garantir que o firewall do servidor permite esta porta

        # Exemplo 3: Equipamento em Modo Mestre
        integra-400-plus:
          name: "INTEGRA 400/PLUS"
          enabled: true
          protocol: ROCHE_HIF
          communication:
            type: SERIAL
            port-name: COM7           # Mapear para a porta COM correta
            # ...
    ```

### 3.3. Construindo a Aplicação
Com a configuração pronta, gere o pacote executável (`.jar`):

```bash
mvn clean package
```
O arquivo final estará em `target/lab-integration-api-0.0.1-SNAPSHOT.jar`.

### 3.4. Executando a Aplicação
1.  Copie o arquivo `.jar` para o servidor de implantação.
2.  Execute o seguinte comando no terminal do servidor:

```bash
java -jar lab-integration-api-0.0.1-SNAPSHOT.jar
```
A aplicação iniciará e os logs no console mostrarão todas as portas TCP e Seriais que foram abertas com sucesso.

## 4. Guia de Testes Completo

Para validar todas as funcionalidades, é necessário simular tanto o LIS quanto os equipamentos.

### 4.1. Ferramentas Necessárias para Teste
*   **Swagger UI (Integrado):** Para testar a API REST (simular o LIS).
*   **Hercules SETUP utility:** Para simular equipamentos TCP/IP e Seriais.
*   **com0com (ou similar):** Para criar pares de portas seriais virtuais no Windows, essenciais para testar a comunicação serial.

### 4.2. Preparando o Ambiente de Teste Serial (com0com)
1.  Instale e abra o `com0com`.
2.  Crie um par de portas virtuais, por exemplo, **COM3 <-> COM5**.
3.  Esta será sua "ponte": a API vai ouvir em uma ponta (ex: COM5) e o Hercules vai se conectar na outra (ex: COM3).

### 4.3. Testando a API REST com Swagger UI
1.  Com a aplicação rodando, acesse **`http://localhost:8080/swagger-ui.html`**.
2.  Use a interface para:
    *   **Criar Ordens (`POST /api/v1/orders`):** Essencial antes de simular o envio de resultados ou queries. Clique em "Try it out", edite o JSON de exemplo e clique em "Execute".
    *   **Consultar Resultados (`GET /api/v1/orders`):** Para verificar se uma ordem foi atualizada após um teste com o Hercules.
    *   **Disparar Modo Mestre (`POST /api/v1/actions/{equipmentId}/request-results`):** Para iniciar a comunicação com o Integra 400/PLUS.

### 4.4. Cenários de Teste (Simulando Equipamentos com Hercules)

#### **Cenário 1: Recepção Passiva (Abbott c8000)**
1.  **Configuração:** API ouvindo na `COM3`, Hercules conectado na `COM5`.
2.  **Swagger:** Crie uma ordem para `sampleId: "ABBOTT01"`, `testType: "GLUCOSE"`.
3.  **Hercules:** Envie a mensagem ASTM de resultado.
    *   **Dica:** Use o modo **HEX** para garantir que todos os caracteres de controle (`<STX>`, `<CR>`, etc.) sejam enviados corretamente.
4.  **Verificação:** Consulte a ordem no Swagger e confirme que o status mudou para `COMPLETED` e os valores do resultado foram preenchidos.

#### **Cenário 2: Host Query Bidirecional (CELL-DYN RUBY)**
1.  **Configuração:** API ouvindo na `COM5`, Hercules conectado na `COM3`.
2.  **Swagger:** Crie uma ordem para `sampleId: "RUBY02"`, `testType: "CBC"`.
3.  **Hercules (Query):** Envie a mensagem ASTM de **requisição de ordem** (registro `Q|`).
4.  **Verificação (Hercules):** Observe a janela `Received/Sent data`. A API deve **responder** com uma mensagem ASTM contendo os detalhes da ordem `RUBY02`.
5.  **Hercules (Resultado):** Envie a mensagem ASTM de **resultado**.
6.  **Verificação (Swagger):** Consulte a ordem e confirme a atualização.

#### **Cenário 3: Comunicação TCP/IP (MAGLUMI X3)**
1.  **Configuração:** API ouvindo na porta TCP `5001`.
2.  **Hercules:** Use a aba **"TCP Client"** e conecte-se a `localhost` (ou o IP do servidor) na porta `5001`.
3.  **Swagger:** Crie uma ordem para `sampleId: "MAGLUMI03"`, `testType: "TSH"`.
4.  **Hercules:** Envie a mensagem HL7 de resultado, encapsulada com os caracteres MLLP (`<VT>` no início, `<FS><CR>` no final).
5.  **Verificação:** Consulte a ordem no Swagger.

#### **Cenário 4: Modo Mestre (INTEGRA 400/PLUS)**
1.  **Configuração:** API ouvindo na `COM5`, Hercules conectado na `COM3`.
2.  **Swagger:**
    *   Crie uma ordem para `sampleId: "INTEGRA04"`, `testType: "123"`.
    *   Execute o endpoint `POST /api/v1/actions/integra-400-plus/request-results`. A requisição ficará "carregando".
3.  **Verificação (Hercules):** Observe a mensagem de **solicitação** chegando da API.
4.  **Hercules:** **Responda** à solicitação enviando a mensagem de resultado do Integra.
5.  **Verificação (Swagger):** A requisição que estava carregando deve completar com sucesso (`200 OK`). Consulte a ordem para confirmar a atualização.

## 5. Logs e Auditoria

*   **Log de Execução:** Localizado em `./logs/lab-integration-api.log`. Contém informações sobre o estado da aplicação, conexões e erros.
*   **Log de Auditoria:** Localizado em `./message_logs/`. Cada mensagem bruta recebida de um equipamento é salva em um arquivo de texto, permitindo rastreabilidade total. A estrutura é `./message_logs/[NOME_EQUIPAMENTO]/[DATA]/[HORA]_message.[EXT]`.
