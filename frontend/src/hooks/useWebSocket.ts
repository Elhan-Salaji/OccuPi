import { useEffect, useRef } from 'react';
import { Client } from '@stomp/stompjs';
import SockJS from 'sockjs-client';
import { useRoomStore } from './useRoomStore';

export function useWebSocket() {
    const { updateRoom, setIsConnected } = useRoomStore();
    const clientRef = useRef<Client | null>(null);

    useEffect(() => {
        const wsUrl = import.meta.env.VITE_API_URL.replace(/\/api$/, '') + '/ws/occupancy';

        const client = new Client({
            webSocketFactory: () => new SockJS(wsUrl),
            onConnect: () => {
                setIsConnected(true);
                client.subscribe('/topic/occupancy', (message) => {
                    try {
                        const data= JSON.parse(message.body);
                        if (typeof data.roomId === 'string' && typeof data.count === 'number') {
                            updateRoom(data.roomId, data.count);
                        }
                    } catch (e) {
                        console.error('Failed to parse occupancy message:', e);
                    }

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
    }, [updateRoom, setIsConnected]);
}
