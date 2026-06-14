import { useState, useEffect } from 'react';
import { Outlet, Link, useLocation } from 'react-router-dom';
import { LayoutDashboard, ChartColumn, DoorOpen, ChevronRight, ChevronLeft, LogOut } from 'lucide-react';

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
            <aside className={`relative bg-[#111827] text-gray-300 flex flex-col justify-between border-r border-[#1F2937] transition-all duration-300 ease-in-out ${isCollapsed ? 'w-24' : 'w-64'}`}>

                {/* button to collapse sidebar */}
                <button
                    onClick={() => setIsCollapsed(!isCollapsed)}
                    className="absolute -right-3 top-7 bg-[#1F2937] border border-[#374151] text-gray-300 hover:text-white p-1 rounded-md z-10 transition-colors shadow-md"
                >
                    {isCollapsed ? <ChevronRight size={18} /> : <ChevronLeft size={18} />}
                </button>

                {/* logo at the top, TO DO: change logo*/}
                <div className="h-20 flex items-center justify-center mb-6 border-b border-[#1F2937] overflow-hidden px-2">
                    <Link to="/dashboard" className="text-xl font-bold text-white hover:opacity-80 transition-opacity cursor-pointer truncate">
                        {/* collapsed: OP - otherwise: OccuPi */}
                        {isCollapsed ? 'OP' : 'OccuPi'}
                    </Link>
                </div>

                {/* lists of navigation items */}
                <nav className="space-y-2 px-4 flex-1 items-center md:items-strech">
                    {navItems.map((item) => {
                        const Icon = item.icon;

                        // here we check if link = right URL
                        const active = location.pathname === item.path;

                        return (
                                <Link
                                    key={item.path}
                                    to={item.path}
                                    title={isCollapsed ? item.label : undefined} // shows name while hovering
                                    className={`flex items-center p-3 rounded-xl font-medium transition-all duration-200 ${
                                        active
                                            ? 'bg-gray-100 text-[#111827] shadow-sm'
                                            : 'hover:bg-[#1F2937] text-gray-400 hover:text-white'
                                    } ${isCollapsed 
                                        ? 'w-12 h-12 mx-auto justify-center' 
                                        : 'h-12 py-4 w-full'}`}
                                >
                                    <Icon size={22} className="shrink-0" />

                                    {/* text disappears if sidebar is collapsed */}
                                    {!isCollapsed && <span className="ml-4 truncate">{item.label}</span>}
                                </Link>
                                );
                                })}
                            </nav>


                {/* lower section */}
                <div className="p-4 border-t border-[#1F2937]">
                <button
                    title={isCollapsed ? 'Logout' : undefined}
                    className={`flex items-center p-3 rounded-xl font-medium w-full transition-all duration-200 hover:bg-red-500/10 text-gray-400 hover:text-red-400 ${isCollapsed ? 'justify-center' : ''}`}
                >
                    <LogOut size={22} className="shrink-0" />
                    {!isCollapsed && <span className="ml-4 truncate">Logout</span>}
                </button>
                </div>
            </aside>

            <main className="flex-1 p-6 md:p-8 overflow-x-hidden">
                <Outlet />
            </main>
        </div>
    );
};