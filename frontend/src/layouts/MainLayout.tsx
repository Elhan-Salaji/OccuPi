import { useState } from "react";
import { Outlet } from 'react-router-dom';
import { Sidebar } from '../components/Sidebar';
import { Navbar } from '../components/Navbar';
import { useWebSocket } from '../hooks/useWebSocket';
import { useFetchRooms } from "../hooks/useFetchRooms";

export const MainLayout = () => {
    useWebSocket();
    useFetchRooms();
    const [mobileNavOpen, setMobileNavOpen] = useState(false);
    return (
        <div className="h-screen flex bg-[#F3F4F6] text-[#1F2937] font-sans overflow-hidden">

            <Sidebar mobileOpen={mobileNavOpen} onMobileClose={() => setMobileNavOpen(false)}/>

            <div className="flex-1 flex flex-col min-w-0">

                <Navbar onMenuClick={() => setMobileNavOpen(true)} />

                <main className="flex-1 p-6 md:p-8 overflow-x-hidden overflow-y-auto">

                    <Outlet />
                </main>

            </div>
        </div>
    );
};
