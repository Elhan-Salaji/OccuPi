import { useState, useEffect } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { LayoutDashboard, ChartColumn, DoorOpen, PanelLeftOpen, PanelLeftClose, LogOut } from 'lucide-react';
import { useAuthStore} from "../hooks/useAuthStore";

export const Sidebar = () => {
    const location = useLocation(); // reads the current location (link gets correspondingly highlighted in color)

    const navigate = useNavigate();

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

    //switch for the pop-up (default: false = closed)
    const [showLogoutDialog, setShowLogoutDialog] = useState(false);

    return (
    <aside className={`relative z-20 bg-[#111827] text-gray-300 flex flex-col justify-between border-r border-[#1F2937] transition-all duration-300 ease-in-out ${isCollapsed ? 'w-20' : 'w-64'}`}>
        {/* background dark blue / test - with dynamic width*/}


        {/* button to collapse sidebar */}
        <button
            onClick={() => setIsCollapsed(!isCollapsed)}
            aria-label={isCollapsed ? "Expand sidebar" : "Collapse sidebar"}
            className="absolute -right-3 top-7 bg-[#1F2937] border border-[#374151] text-gray-300 hover:text-white p-1 rounded-md z-10 transition-colors shadow-md"
        >
            {isCollapsed ? <PanelLeftOpen size={18} /> : <PanelLeftClose size={18} />}
        </button>

        {/* logo at the top, TO DO: change logo*/}
        <div className="h-20 flex items-center justify-center mb-6 border-b border-[#1F2937] overflow-hidden px-2">
            <Link to="/dashboard" className="text-xl font-bold text-white hover:opacity-80 transition-opacity cursor-pointer truncate">
                {/* collapsed: OP - otherwise: OccuPi */}
                {isCollapsed ? 'OP' : 'OccuPi'}
            </Link>
        </div>

        {/* lists of navigation items */}
        <nav className="space-y-2 px-4 flex-1 items-center md:items-stretch">
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
                onClick={() => setShowLogoutDialog(true)}
                title={isCollapsed ? 'Logout' : undefined}
                className={`flex items-center rounded-xl font-medium transition-all duration-200 hover:bg-red-500/20 text-gray-400 hover:text-red-300 ${
                    isCollapsed
                        ? 'w-12 h-12 mx-auto justify-center' // like the buttons above
                        : 'h-12 px-4 w-full'
                }`}
            >
                <LogOut size={22} className="shrink-0" />
                {!isCollapsed && <span className="ml-4 truncate">Log out</span>}
            </button>
            {showLogoutDialog && (
                <div className="fixed inset-0 bg-black/60 z-50 flex items-center justify-center">
                    {/* White Box */}
                    <div className="bg-white rounded-xl shadow-2xl p-6 max-w-sm w-full mx-4">
                        <h2 className="text-xl font-bold text-gray-900 mb-2">Log out</h2>
                        <p className="text-gray-500 mb-6">Do you really want to log out of the system?</p>

                        <div className="flex justify-end space-x-3">
                            <button
                                onClick={() => setShowLogoutDialog(false)}
                                className="px-4 py-2 text-sm font-medium text-gray-700 bg-gray-100 hover:bg-gray-200 rounded-lg transition-colors"
                            >
                                Cancel
                            </button>
                            <button
                                onClick={() => {
                                    useAuthStore.getState().logout();
                                    setShowLogoutDialog(false);
                                    navigate('/login')
                                }}
                                className="px-4 py-2 text-sm font-medium text-white bg-red-600 hover:bg-red-700 rounded-lg transition-colors"
                            >
                                Log out
                            </button>
                        </div>
                    </div>
                </div>
            )}
        </div>
    </aside>
);
};

