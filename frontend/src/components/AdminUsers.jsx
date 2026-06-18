import React, { useState, useEffect } from 'react';
import { Search, Shield, User, Loader2, Key, Phone, Mail, CheckCircle } from 'lucide-react';
import toast from 'react-hot-toast';
import api from '../services/api';
import { UsersSkeleton } from './SkeletonLoader';

const AdminUsers = () => {
  const [users, setUsers] = useState([]);
  const [loading, setLoading] = useState(true);
  const [searchTerm, setSearchTerm] = useState('');
  const [updatingUserId, setUpdatingUserId] = useState(null);

  // Helper to extract user profile (for self-update safety checks)
  const getLoggedInUser = () => {
    const token = localStorage.getItem('token');
    if (!token) return null;
    try {
      const base64Url = token.split('.')[1];
      const base64 = base64Url.replace(/-/g, '+').replace(/_/g, '/');
      const jsonPayload = decodeURIComponent(window.atob(base64).split('').map(function(c) {
          return '%' + ('00' + c.charCodeAt(0).toString(16)).slice(-2);
      }).join(''));
      return JSON.parse(jsonPayload);
    } catch (e) {
      return null;
    }
  };

  const loggedInUser = getLoggedInUser();

  const fetchUsers = async () => {
    setLoading(true);
    try {
      const response = await api.get('/users');
      setUsers(Array.isArray(response.data) ? response.data : []);
    } catch (error) {
      console.error('Error fetching users:', error);
      toast.error('Failed to load users list');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchUsers();
  }, []);

  const handleRoleChange = async (userId, userEmail, userPhone, currentRoles, newRole) => {
    // Prevent locking out yourself
    const isSelf = loggedInUser && (loggedInUser.sub === userEmail || loggedInUser.sub === userPhone);
    if (isSelf && newRole !== 'ADMIN') {
      const confirmChange = window.confirm(
        'Warning: You are about to remove the ADMIN role from your own account. This will lock you out of this dashboard immediately. Are you sure you want to proceed?'
      );
      if (!confirmChange) {
        // Reset the select dropdown UI
        fetchUsers();
        return;
      }
    }

    setUpdatingUserId(userId);
    try {
      const payload = {
        roles: [newRole]
      };
      await api.put(`/users/${userId}/role`, payload);
      toast.success(`Role updated to ${newRole} successfully`);
      
      // Update local state
      setUsers(prevUsers =>
        prevUsers.map(user =>
          user.id === userId ? { ...user, roles: [newRole] } : user
        )
      );
      
      // If self-demoted, redirect to home page
      if (isSelf && newRole !== 'ADMIN') {
        toast.loading('Redirecting since you are no longer an Admin...');
        setTimeout(() => {
          localStorage.removeItem('token');
          window.location.href = '/login';
        }, 1500);
      }
    } catch (error) {
      console.error('Error updating role:', error);
      toast.error(error.response?.data || 'Failed to update user role');
      // Re-fetch users to revert UI dropdown state
      fetchUsers();
    } finally {
      setUpdatingUserId(null);
    }
  };

  const filteredUsers = users.filter(user => {
    const search = searchTerm.toLowerCase();
    const nameMatches = user.name ? user.name.toLowerCase().includes(search) : false;
    const emailMatches = user.email ? user.email.toLowerCase().includes(search) : false;
    const phoneMatches = user.phone ? user.phone.includes(search) : false;
    const roleMatches = user.roles ? user.roles.some(r => r.toLowerCase().includes(search)) : false;
    
    return nameMatches || emailMatches || phoneMatches || roleMatches;
  });

  const getRoleColor = (roles) => {
    if (!roles || roles.length === 0) return 'bg-gray-100 text-gray-800';
    if (roles.includes('ADMIN')) return 'bg-red-100 text-red-800 border-red-200';
    if (roles.includes('AGENT')) return 'bg-purple-100 text-purple-800 border-purple-200';
    return 'bg-green-100 text-green-800 border-green-200';
  };

  const getProviderColor = (provider) => {
    switch (provider?.toUpperCase()) {
      case 'GOOGLE':
        return 'bg-blue-50 text-blue-700 border border-blue-200';
      case 'PHONE':
        return 'bg-yellow-50 text-yellow-800 border border-yellow-200';
      case 'LOCAL':
      default:
        return 'bg-gray-50 text-gray-700 border border-gray-200';
    }
  };

  if (loading) {
    return <UsersSkeleton />;
  }

  return (
    <div className="space-y-6">
      {/* Header and Search */}
      <div className="flex flex-col sm:flex-row justify-between items-start sm:items-center gap-4 border-b pb-4">
        <div>
          <h2 className="text-2xl font-bold text-gray-900 flex items-center gap-2">
            <Shield className="h-6 w-6 text-blue-600" />
            User Roles & Permissions
          </h2>
          <p className="text-gray-500 text-sm mt-1">
            Search users and assign permissions (Admin, Agent, User) instantly.
          </p>
        </div>
        <div className="relative w-full sm:w-72">
          <input
            id="user-search"
            type="text"
            placeholder="Search by name, email or role..."
            value={searchTerm}
            onChange={(e) => setSearchTerm(e.target.value)}
            className="w-full pl-10 pr-4 py-2 border rounded-md shadow-sm focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-blue-500 bg-white"
          />
          <Search className="absolute left-3 top-2.5 h-5 w-5 text-gray-400" />
        </div>
      </div>

      {/* Users Count Summary */}
      <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
        <div className="bg-blue-50 border border-blue-100 rounded-lg p-4 flex items-center gap-3">
          <div className="p-2.5 bg-blue-100 text-blue-600 rounded-md">
            <User className="h-5 w-5" />
          </div>
          <div>
            <div className="text-xs text-blue-600 uppercase font-bold tracking-wider">Total Registered</div>
            <div className="text-2xl font-bold text-blue-900">{users.length}</div>
          </div>
        </div>
        <div className="bg-red-50 border border-red-100 rounded-lg p-4 flex items-center gap-3">
          <div className="p-2.5 bg-red-100 text-red-600 rounded-md">
            <Shield className="h-5 w-5" />
          </div>
          <div>
            <div className="text-xs text-red-600 uppercase font-bold tracking-wider">Administrators</div>
            <div className="text-2xl font-bold text-red-900">
              {users.filter(u => u.roles?.includes('ADMIN')).length}
            </div>
          </div>
        </div>
        <div className="bg-purple-50 border border-purple-100 rounded-lg p-4 flex items-center gap-3">
          <div className="p-2.5 bg-purple-100 text-purple-600 rounded-md">
            <Key className="h-5 w-5" />
          </div>
          <div>
            <div className="text-xs text-purple-600 uppercase font-bold tracking-wider">Agents / Staff</div>
            <div className="text-2xl font-bold text-purple-900">
              {users.filter(u => u.roles?.includes('AGENT')).length}
            </div>
          </div>
        </div>
      </div>

      {/* Responsive Table */}
      <div className="bg-white rounded-lg border border-gray-200 shadow-sm overflow-hidden">
        <div className="overflow-x-auto">
          <table className="min-w-full divide-y divide-gray-200">
            <thead className="bg-gray-50">
              <tr>
                <th scope="col" className="px-6 py-3 text-left text-xs font-semibold text-gray-500 uppercase tracking-wider">User Info</th>
                <th scope="col" className="px-6 py-3 text-left text-xs font-semibold text-gray-500 uppercase tracking-wider">Contact</th>
                <th scope="col" className="px-6 py-3 text-left text-xs font-semibold text-gray-500 uppercase tracking-wider">Auth Provider</th>
                <th scope="col" className="px-6 py-3 text-left text-xs font-semibold text-gray-500 uppercase tracking-wider">Current Role</th>
                <th scope="col" className="px-6 py-3 text-left text-xs font-semibold text-gray-500 uppercase tracking-wider">Actions / Assign Role</th>
              </tr>
            </thead>
            <tbody className="bg-white divide-y divide-gray-200">
              {filteredUsers.length > 0 ? (
                filteredUsers.map((user) => {
                  const currentRole = user.roles && user.roles.length > 0 ? user.roles[0] : 'USER';
                  const isUpdating = updatingUserId === user.id;

                  return (
                    <tr key={user.id} className="hover:bg-gray-50 transition-colors">
                      {/* User Info */}
                      <td className="px-6 py-4 whitespace-nowrap">
                        <div className="flex items-center gap-3">
                          <div className="w-10 h-10 rounded-full bg-blue-100 text-blue-700 flex items-center justify-center font-bold text-lg overflow-hidden border border-blue-200">
                            {user.picture ? (
                              <img src={user.picture} alt={user.name} className="w-full h-full object-cover" />
                            ) : (
                              (user.name ? user.name.charAt(0) : (user.email ? user.email.charAt(0) : '?')).toUpperCase()
                            )}
                          </div>
                          <div>
                            <div className="text-sm font-semibold text-gray-900">{user.name || 'Unnamed User'}</div>
                            <div className="text-xs text-gray-500 font-mono">ID: {user.id}</div>
                          </div>
                        </div>
                      </td>

                      {/* Contact Info */}
                      <td className="px-6 py-4 whitespace-nowrap">
                        <div className="space-y-1">
                          {user.email && (
                            <div className="text-sm text-gray-700 flex items-center gap-1.5">
                              <Mail className="h-3.5 w-3.5 text-gray-400" />
                              {user.email}
                            </div>
                          )}
                          {user.phone && (
                            <div className="text-sm text-gray-700 flex items-center gap-1.5">
                              <Phone className="h-3.5 w-3.5 text-gray-400" />
                              {user.phone}
                            </div>
                          )}
                          {!user.email && !user.phone && (
                            <span className="text-xs text-gray-400 italic">No contact details</span>
                          )}
                        </div>
                      </td>

                      {/* Auth Provider */}
                      <td className="px-6 py-4 whitespace-nowrap">
                        <span className={`inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-semibold uppercase ${getProviderColor(user.provider)}`}>
                          {user.provider || 'LOCAL'}
                        </span>
                      </td>

                      {/* Current Role */}
                      <td className="px-6 py-4 whitespace-nowrap">
                        <span className={`inline-flex items-center px-3 py-1 rounded-full text-xs font-bold border uppercase ${getRoleColor(user.roles)}`}>
                          {currentRole}
                        </span>
                      </td>

                      {/* Assign Role Dropdown */}
                      <td className="px-6 py-4 whitespace-nowrap">
                        <div className="flex items-center gap-3">
                          <select
                            value={currentRole}
                            disabled={isUpdating}
                            onChange={(e) => handleRoleChange(user.id, user.email, user.phone, user.roles, e.target.value)}
                            className="rounded-md border-gray-300 shadow-sm focus:border-blue-500 focus:ring-blue-500 px-3 py-1.5 text-sm bg-white border cursor-pointer font-medium text-gray-800 disabled:opacity-50"
                          >
                            <option value="USER">USER (Customer)</option>
                            <option value="AGENT">AGENT (Staff)</option>
                            <option value="ADMIN">ADMIN (Manager)</option>
                          </select>
                          
                          {isUpdating && (
                            <Loader2 className="h-5 w-5 text-blue-600 animate-spin" />
                          )}
                        </div>
                      </td>
                    </tr>
                  );
                })
              ) : (
                <tr>
                  <td colSpan="5" className="px-6 py-10 text-center text-gray-500">
                    No users found matching your search.
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
};

export default AdminUsers;
