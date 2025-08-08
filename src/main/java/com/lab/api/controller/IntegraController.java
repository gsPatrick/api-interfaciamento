package com.lab.api.controller;

import com.lab.api.integration.EquipmentListenerManager;
import com.lab.api.service.ProtocolDispatcherMessageHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.Optional;

@RestController
@RequestMapping("/api/v1/actions")
@RequiredArgsConstructor
public class IntegraController {

    private final EquipmentListenerManager listenerManager;
    private final ProtocolDispatcherMessageHandler dispatcher;

    @PostMapping("/{equipmentId}/request-results")
    public ResponseEntity<String> requestResults(@PathVariable String equipmentId) {

        // Exemplo de como construir a mensagem de requisição de resultados para o Integra
        String requestBlock = "\u0001\n09_COBAS_INTEGRA..._09\n\u0002\n10_01\n\u0003\n1\n625\n\u0004\n";

        Optional<String> response = listenerManager.sendRequest(equipmentId, requestBlock);

        if (response.isPresent()) {
            // Delega a resposta para o dispatcher processar e salvar no banco
            dispatcher.handle(response.get(), listenerManager.getEquipmentConfig(equipmentId));
            return ResponseEntity.ok("Resultados requisitados e recebidos com sucesso.");
        } else {
            return ResponseEntity.status(504).body("Falha ao receber resposta do equipamento " + equipmentId);
        }
    }
}