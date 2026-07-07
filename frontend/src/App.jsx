// Redeploy trigger: Microservices Migration Complete
import React from 'react';
import { BrowserRouter as Router, Routes, Route, Navigate } from 'react-router-dom';
import { Toaster } from 'react-hot-toast';
import Home from './pages/Home';
import Login from './pages/Login';
import Register from './pages/Register';
import ForgotPassword from './pages/ForgotPassword';
import OAuth2RedirectHandler from './pages/OAuth2RedirectHandler';
import Admin from './pages/Admin';
import ImageGallery from './pages/ImageGallery';
import PackageDetails from './pages/PackageDetails';
import TrustPortal from './pages/TrustPortal';

import MainLayout from './components/MainLayout';

const ProtectedRoute = ({ children }) => {
  const token = localStorage.getItem('token');
  if (!token) {
    return <Navigate to="/login" replace />;
  }
  return children;
};

function App() {
  return (
    <Router>
      <Toaster position="top-right" />
      <Routes>
        <Route path="/" element={<MainLayout><Home /></MainLayout>} />
        <Route path="/packages/:id" element={<MainLayout><PackageDetails /></MainLayout>} />
        <Route path="/trust" element={<MainLayout><TrustPortal /></MainLayout>} />
        <Route path="/login" element={<Login />} />
        <Route path="/register" element={<Register />} />
        <Route path="/forgot-password" element={<ForgotPassword />} />
        <Route path="/oauth2/redirect" element={<OAuth2RedirectHandler />} />
        <Route
          path="/admin"
          element={
            <ProtectedRoute>
              <Admin />
            </ProtectedRoute>
          }
        />
        <Route
          path="/admin/images"
          element={
            <ProtectedRoute>
              <ImageGallery />
            </ProtectedRoute>
          }
        />
      </Routes>
    </Router>
  );
}

export default App;
