/*
 * SteVe - SteckdosenVerwaltung - https://github.com/steve-community/steve
 * Copyright (C) 2013-2026 SteVe Community Team
 * All Rights Reserved.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package de.rwth.idsg.steve.ocpp.ws.flutter;


import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class OcppEventService {

    @Autowired
    private ObjectMapper mapper;

    @Autowired
    private OcppMessageStore messageStore;

    @Autowired
    private FlutterSessionManager sessionManager;

    public void publish(String event,
                        String chargeBoxId,
                        Object payload,
                        String direction) {

        try {

            Map<String, Object> msg = new HashMap<>();
            msg.put("event", event);
            msg.put("chargeBoxId", chargeBoxId);
            msg.put("direction", direction);
            msg.put("timestamp", System.currentTimeMillis());
            msg.put("data", payload);

            String json = mapper.writeValueAsString(msg);

            messageStore.add(chargeBoxId, event, direction, json);
            sessionManager.broadcast(chargeBoxId, json);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}