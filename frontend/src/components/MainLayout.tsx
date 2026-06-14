import { useState, useEffect } from 'react';
import { Outlet, Link, useLocation } from 'react-router-dom';
import { LayoutDashboard, ChartColumn, DoorOpen, ChevronRight, ChevronLeft } from 'lucide-react';

export const MainLayout = () => {
    const location = useLocation(); // reads the current location (link gets correspondingly highlighted in color)

    // memory of sidebar state
    const [isCollapsed, setIsCollapsed] = useState(() => {
        const saved = localStorage.getItem('sidebar-collapsed');
        return saved === 'true'; // converts the string "true" into a real boolean.
    });

    // save new state of sidebar in browser memory
    useEffect(() => {
        localStorage.setItem('sidebar-collapsed', String(isCollapsed));
    }, [isCollapsed]);

    // path links for the sidebar -> links to the other pages
    // it's an array/list - easier and shorter
    const navItems = [
        { path: '/dashboard', label: 'Dashboard', icon: LayoutDashboard },
        { path: '/rooms', label: 'Räume', icon: DoorOpen },
        { path: '/analytics', label: 'Analytics', icon: ChartColumn },
    ];

    return (
        <div className="min-h-screen flex bg-[#F3F4F6] text-[#1F2937] font-sans">

            {/* background dark blue / test - with dynamic width*/}
            <aside className={`bg-[#111827] text-gray-300 flex flex-col border-r border-[#1F2937] transition-all duration-300 ease-in-out ${isCollapsed ? 'w-20' : 'w-64'}`}>
                {/* logo at the top, TO DO: change logo*/}
                <div className="h-12 flex items-center px-2 mb-6 border-b border-[#1F2937]">
                    <Link to="/dashboard"
                          className="text-xl font-bold text-white hover:opacity-80 transition-opacity cursor-pointer">OccuPi</Link>

                {/* Der Button zum Ein- und Ausklappen */}
                <button
                onClick={() => setIsCollapsed(!isCollapsed)}
                className={"p-1.5 rounded-lg hover:bg-[#1F2937] text-gray-400 hover:text-white transitions-colors"}
                >
                    {isCollapsed ? <ChevronRight size={18} /> : <ChevronLeft size={18} />}
                    </button>
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
                                    title={isCollapsed ? item.label : undefined} // shows name while hovering
                                    className={`flex items-center py-3 px-4 rounded-xl font-medium transition-all duration-200 ${
                                        active
                                            ? 'bg-white text-[#111827] font-semibold shadow-sm'
                                            : 'hover:bg-[#1F2937] hover:text-white text-gray-400'
                                    }`}
                                >
                                    <Icon size={20} className="shrink-0" />

                                    {/* text disappears if sidebar is collapsed */}
                                    {!isCollapsed && <span className="ml-4 truncate">{item.label}</span>}
                                </Link>
                                );
                                })}
                            </nav>
                    </aside>

                        <main className="flex-1 p-6 md:p-8 overflow-x-hidden">
                            <Outlet />
                        </main>
                    </div>
                    );
                    };