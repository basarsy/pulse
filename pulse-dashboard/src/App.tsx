import React from 'react';
import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom';
import { AuthProvider, useAuth } from './context/AuthContext';
import { LiveProvider } from './context/LiveContext';
import { NotificationProvider } from './context/NotificationContext';
import Layout from './components/Layout';
import LoginPage from './pages/LoginPage';
import RegisterPage from './pages/RegisterPage';
import DashboardPage from './pages/DashboardPage';
import VehicleListPage from './pages/VehicleListPage';
import VehicleDetailPage from './pages/VehicleDetailPage';
import RolloutListPage from './pages/RolloutListPage';
import RolloutDetailPage from './pages/RolloutDetailPage';

const ProtectedRoute = ({ children, adminOnly = false }: { children: React.ReactNode; adminOnly?: boolean }) => {
  const { isAuthenticated, isAdmin, restoring } = useAuth();
  if (restoring) return <div className="page-content">Loading…</div>;
  if (!isAuthenticated) return <Navigate to="/login" replace />;
  if (adminOnly && !isAdmin) return <Navigate to="/" replace />;
  return <>{children}</>;
};

const AppContent = () => (
  <Routes>
    <Route path="/login" element={<LoginPage />} />
    <Route path="/register" element={<RegisterPage />} />

    <Route path="/" element={<ProtectedRoute><Layout /></ProtectedRoute>}>
      <Route index element={<DashboardPage />} />
      <Route path="vehicles" element={<VehicleListPage />} />
      <Route path="vehicles/:id" element={<VehicleDetailPage />} />
      <Route path="rollouts" element={<ProtectedRoute adminOnly><RolloutListPage /></ProtectedRoute>} />
      <Route path="rollouts/:id" element={<ProtectedRoute adminOnly><RolloutDetailPage /></ProtectedRoute>} />
    </Route>
    <Route path="*" element={<Navigate to="/" replace />} />
  </Routes>
);

export default function App() {
  return (
    <BrowserRouter>
      <AuthProvider>
        <LiveProvider>
          <NotificationProvider>
            <AppContent />
          </NotificationProvider>
        </LiveProvider>
      </AuthProvider>
    </BrowserRouter>
  );
}
