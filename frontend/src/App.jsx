import { NavLink, Navigate, Route, Routes, useNavigate } from 'react-router-dom';
import { auth } from './api';
import Login from './pages/Login';
import Overview from './pages/Overview';
import Goals from './pages/Goals';
import Fitness from './pages/Fitness';
import Settings from './pages/Settings';

function Shell({ children }) {
  const nav = useNavigate();
  return (
    <div className="wrap">
      <header>
        <h1>🔥 Consistency Tracker</h1>
        <nav>
          <NavLink to="/">Overview</NavLink>
          <NavLink to="/goals">Goals</NavLink>
          <NavLink to="/fitness">Fitness</NavLink>
          <NavLink to="/settings">Settings</NavLink>
          <button className="ghost" onClick={() => { auth.clear(); nav('/login'); }}>Log out</button>
        </nav>
      </header>
      {children}
    </div>
  );
}

const Guard = ({ children }) => (auth.get() ? <Shell>{children}</Shell> : <Navigate to="/login" replace />);

export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<Login />} />
      <Route path="/" element={<Guard><Overview /></Guard>} />
      <Route path="/goals" element={<Guard><Goals /></Guard>} />
      <Route path="/fitness" element={<Guard><Fitness /></Guard>} />
      <Route path="/settings" element={<Guard><Settings /></Guard>} />
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}
