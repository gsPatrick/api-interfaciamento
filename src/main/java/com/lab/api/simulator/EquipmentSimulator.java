package com.lab.api.simulator;

import com.fazecast.jSerialComm.SerialPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Component
@Profile("dev") // Este componente só será ativado se o perfil 'dev' estiver ativo
@Slf4j
public class EquipmentSimulator implements CommandLineRunner {

    // Caracteres de controle do protocolo ASTM e MLLP
    private static final byte ENQ = 0x05;
    private static final byte ACK = 0x06;
    private static final byte EOT = 0x04;
    private static final byte STX = 0x02;
    private static final byte ETX = 0x03;
    private static final byte CR = 0x0D;
    private static final byte LF = 0x0A;
    private static final byte VT = 0x0B;
    private static final byte FS = 0x1C;

    @Override
    public void run(String... args) {
        log.info("************************************************************");
        log.info("MODO DE DESENVOLVIMENTO ATIVO: Iniciando simulador de equipamentos...");
        log.info("************************************************************");

        // Agenda a simulação para ocorrer após um delay, para dar tempo da API subir
        Executors.newSingleThreadScheduledExecutor().schedule(this::simulateAbbottC8000, 15, TimeUnit.SECONDS);
        Executors.newSingleThreadScheduledExecutor().schedule(this::simulateMaglumiX3, 20, TimeUnit.SECONDS);
    }
    private void simulateAbbottC8000() {
        // ===== ALTERAÇÃO IMPORTANTE AQUI =====
        String portNameToSimulate = "COM5"; // <---- ALTERADO DE COM11 PARA COM5
        // =====================================

        log.info("[SIMULADOR] Iniciando simulação para Abbott c8000 na porta {}", portNameToSimulate);
        SerialPort sp = SerialPort.getCommPort(portNameToSimulate);
        sp.setBaudRate(9600);
        sp.setNumDataBits(8);
        sp.setNumStopBits(1);
        sp.setParity(SerialPort.NO_PARITY);

        if (!sp.openPort()) {
            log.error("[SIMULADOR] Falha ao abrir a porta {}. Verifique se o com0com está configurado (COM3 <=> COM5).", portNameToSimulate);
            return;
        }

        try (OutputStream out = sp.getOutputStream()) {
            // A mensagem que o simulador envia deve corresponder ao que o teste espera
            String astmMessage = "P|1||12345||DOE^JOHN\r" +
                    "O|1|SAMPLE123||^^^GLUCOSE|R|||||||F\r" +
                    "R|1|^^^GLUCOSE|105.7|mg/dL|70-110|N||F|||20250807103000\r" +
                    "L|1|F\r";

            log.info("[SIMULADOR Abbott] Enviando ENQ...");
            out.write(ENQ);
            out.flush();

            Thread.sleep(1000); // Espera um pouco pelo ACK da nossa API

            log.info("[SIMULADOR Abbott] Enviando mensagem de resultado ASTM...");
            out.write(STX);
            out.write(astmMessage.getBytes());
            out.write(ETX);
            out.write(CR);
            out.write(LF);
            out.write(EOT);
            out.flush();
            log.info("[SIMULADOR Abbott] Simulação concluída.");

        } catch (Exception e) {
            log.error("[SIMULADOR Abbott] Erro na simulação: {}", e.getMessage());
        } finally {
            sp.closePort();
        }
    }

    private void simulateMaglumiX3() {
        log.info("[SIMULADOR] Iniciando simulação para MAGLUMI X3 em localhost:5001...");
        try (Socket socket = new Socket("localhost", 5001);
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {

            // Mensagem HL7 de resultado de TSH
            String hl7Content = "MSH|^~\\&|MAGLUMI_SIM|LAB|LAB-API|MAIN|202508071100||ORU^R01|MSG001|P|2.5\r" +
                    "PID|1||SAMPLE456||SMITH^JANE||19800101|F\r" +
                    "OBR|1||||TSH\r" +
                    "OBX|1|NM|TSH||2.5|uIU/mL|0.4-4.0|N|||F|||20250807110500\r";

            // Monta a mensagem no formato MLLP
            String mllpMessage = (char) VT + hl7Content + (char) FS + (char) CR;

            log.info("[SIMULADOR Maglumi] Enviando mensagem de resultado HL7...");
            out.print(mllpMessage);
            out.flush();
            log.info("[SIMULADOR Maglumi] Simulação concluída.");

        } catch (Exception e) {
            log.error("[SIMULADOR Maglumi] Erro na simulação: {}", e.getMessage());
        }
    }
}