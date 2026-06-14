import { Outlet } from 'react-router-dom';

export const MainLayout = () => {

    return (
        <div className="min-h-screen flex bg-[#F3F4F6] text-[#1F2937]">
            <aside className="w-64 bg-[#111827] text-white p-4">
                <h1 className="text-xl font-bold">OccuPi</h1>
                <p className="text-xs text-gray-400">Sidebar Base</p>
            </aside>
            <main className="flex-1 p-6">
                <Outlet />
            </main>
        </div>
    );
};