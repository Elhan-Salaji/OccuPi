import { Outlet } from 'react-router-dom';
import { Sidebar } from '../components/Sidebar';
import { Navbar } from '../components/Navbar';

export const MainLayout = () => {
    return (
        <div className="min-h-screen flex bg-[#F3F4F6] text-[#1F2937] font-sans">

                <Sidebar />

            <div className="flex-1 flex flex-col min-w-0">

                <Navbar />

            <main className="flex-1 p-6 md:p-8 overflow-x-hidden overflow-y-auto"> {/**/}

                <Outlet />
            </main>

            </div>
        </div>
    );
};
