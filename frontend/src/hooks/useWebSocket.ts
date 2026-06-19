import { useEffect, useRef, useState } from 'react';
import { Client } from '@stomp/stompjs';
import SockJS from 'sockjs-client';
import { useRoomStore } from './useRoomStore';
import type { Occupancy } from '../types/room';

export function useWebSocket() {
    const [isConnected, setIsConnected] = useState(false);
    const { updateRoom } = useRoomStore();
    const clientRef = useRef<Client | null>(null);

    useEffect(() => {
        const wsUrl = import.meta.env.VITE_API_URL.replace(/\/api$/, '') + '/ws';

        const client = new Client({
            webSocketFactory: () => new SockJS(wsUrl),
            onConnect: () => {
                setIsConnected(true);
                client.subscribe('/topic/occupancy', (message) => {
                    const data: Occupancy = JSON.parse(message.body);
                    updateRoom(data.roomId, data.count);
                });
            },
            onDisconnect: () => setIsConnected(false),
            reconnectDelay: 5000,
        });

        client.activate();
        clientRef.current = client;

        return () => {
            client.deactivate();
        };
    }, [updateRoom]);
    return { isConnected };
}
