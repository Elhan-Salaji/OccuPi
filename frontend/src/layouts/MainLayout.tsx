import { Outlet } from 'react-router-dom';
import { Sidebar } from '../components/Sidebar';

export const MainLayout = () => {
    return (
        <div className="min-h-screen flex bg-[#F3F4F6] text-[#1F2937] font-sans">

            <Sidebar />

            <main className="flex-1 p-6 md:p-8 overflow-x-hidden">
                <Outlet />
            </main>

        </div>
    );
};
