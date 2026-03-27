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

import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class FlutterSessionManager {

    private final Map<String, Set<WebSocketSession>> sessions = new ConcurrentHashMap<>();

    public void add(String cpId, WebSocketSession session) {
        sessions.computeIfAbsent(cpId, k -> ConcurrentHashMap.newKeySet()).add(session);
    }

    public void remove(String cpId, WebSocketSession session) {
        Optional.ofNullable(sessions.get(cpId)).ifPresent(set -> set.remove(session));
    }

    public void broadcast(String cpId, String message) {
        Optional.ofNullable(sessions.get(cpId)).ifPresent(set ->
                set.forEach(session -> {
                    try {
                        if (session.isOpen()) {
                            session.sendMessage(new TextMessage(message));
                        }
                    } catch (Exception ignored) {
                    }
                })
        );
    }
}