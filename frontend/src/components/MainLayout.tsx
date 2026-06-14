import { Outlet, Link, useLocation } from 'react-router-dom';
import { LayoutDashboard, ChartColumn, DoorOpen } from 'lucide-react';

export const MainLayout = () => {
    const location = useLocation(); // reads the current location (link gets correspondingly highlighted in color)

    // path links for the sidebar -> links to the other pages
    // it's an array/list - easier and shorter
    const navItems = [
        { path: '/dashboard', label: 'Dashboard', icon: LayoutDashboard },
        { path: '/rooms', label: 'Räume', icon: DoorOpen },
        { path: '/analytics', label: 'Analytics', icon: ChartColumn },
    ];

    return (
        <div className="min-h-screen flex bg-[#F3F4F6] text-[#1F2937] font-sans">

            {/* background dark blue / test */}
            <aside className="w-64 bg-[#111827] text-gray-300 flex flex-col border-r border-[#1F2937] p-4">

                {/* logo at the top, TO DO: change logo*/}
                <div className="h-12 flex items-center px-2 mb-6 border-b border-[#1F2937]">
                    <Link to="/dashboard"
                          className="text-xl font-bold text-white hover:opacity-80 transition-opacity cursor-pointer">OccuPi</Link>
                </div>

                {/* lists of navigation items */}
                <nav className="space-y-1.5 flex-1">
                    {navItems.map((item) => {
                        const Icon = item.icon;

                        // here we check if link = right URL
                        const active = location.pathname === item.path;

                        return (
                            <Link
                                key={item.path}
                                to={item.path}
                                className={`flex items-center py-3 px-4 rounded-xl font-medium transition-all duration-200 ${
                                    active
                                        ? 'bg-white text-[#111827] font-semibold shadow-sm'
                                        : 'hover:bg-[#1F2937] hover:text-white text-gray-400'
                                }`}
                            >
                                <Icon size={20} />
                                <span className="ml-4">{item.label}</span>
                            </Link>
                        );
                    })}
                </nav>
            </aside>

            {/* main area on the right */}
            <main className="flex-1 p-6 md:p-8 overflow-x-hidden">
                <Outlet />
            </main>
        </div>
    );
};