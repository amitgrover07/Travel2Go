import React, { useState, useEffect } from 'react';
import { 
  Hotel, Car, Compass, MapPin, Activity, Plane, Ship, Train, 
  Camera, Coffee, Utensils, Tent, Ticket, Shield, HelpCircle, 
  Plus, Edit2, Trash2, X, AlertCircle
} from 'lucide-react';
import toast from 'react-hot-toast';
import api from '../services/api';

const ICON_MAP = {
  Hotel,
  Car,
  Compass,
  MapPin,
  Activity,
  Plane,
  Ship,
  Train,
  Camera,
  Coffee,
  Utensils,
  Tent,
  Ticket,
  Shield,
  HelpCircle
};

export default function AdminConfiguratorCategories() {
  const [categories, setCategories] = useState([]);
  const [loading, setLoading] = useState(true);
  const [name, setName] = useState('');
  const [selectedIcon, setSelectedIcon] = useState('Hotel');
  const [editingId, setEditingId] = useState(null);
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    fetchCategories();
  }, []);

  const fetchCategories = async () => {
    try {
      setLoading(true);
      const res = await api.get('/configurator-categories');
      setCategories(Array.isArray(res.data) ? res.data : []);
    } catch (err) {
      console.error('Failed to load categories', err);
      toast.error('Failed to load categories');
    } finally {
      setLoading(false);
    }
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (!name.trim()) {
      toast.error('Category name is required');
      return;
    }
    setSaving(true);
    const payload = { name: name.trim(), icon: selectedIcon };
    try {
      if (editingId) {
        await api.put(`/configurator-categories/${editingId}`, payload);
        toast.success('Category updated successfully');
      } else {
        await api.post('/configurator-categories', payload);
        toast.success('Category created successfully');
      }
      setName('');
      setSelectedIcon('Hotel');
      setEditingId(null);
      fetchCategories();
    } catch (err) {
      toast.error(err.response?.data?.message || 'Failed to save category');
    } finally {
      setSaving(false);
    }
  };

  const handleEdit = (category) => {
    setEditingId(category.id);
    setName(category.name);
    setSelectedIcon(category.icon || 'Hotel');
  };

  const handleDelete = async (id) => {
    if (!window.confirm('Are you sure you want to delete this category?')) return;
    try {
      await api.delete(`/configurator-categories/${id}`);
      toast.success('Category deleted successfully');
      fetchCategories();
    } catch (err) {
      toast.error('Failed to delete category');
    }
  };

  const handleCancel = () => {
    setEditingId(null);
    setName('');
    setSelectedIcon('Hotel');
  };

  return (
    <div className="flex flex-col lg:flex-row gap-8">
      {/* Form (Left Column) */}
      <div className="lg:w-1/3 bg-white p-6 rounded-2xl border border-gray-100 shadow-sm">
        <h3 className="text-lg font-bold text-gray-900 mb-5 pb-2 border-b border-gray-100">
          {editingId ? 'Edit Travel Category' : 'Create Travel Category'}
        </h3>
        <form onSubmit={handleSubmit} className="space-y-6">
          <div>
            <label className="block text-xs font-bold text-gray-500 uppercase tracking-wider mb-2 ml-1">
              Category Name
            </label>
            <input
              type="text"
              required
              value={name}
              onChange={(e) => setName(e.target.value)}
              placeholder="e.g. Hotel Stay, Daily Taxi"
              className="w-full px-4 py-2.5 bg-gray-50 border border-gray-200 rounded-xl focus:ring-2 focus:ring-teal-500 focus:border-teal-500 transition-all text-sm outline-none"
            />
          </div>

          <div>
            <label className="block text-xs font-bold text-gray-500 uppercase tracking-wider mb-3 ml-1">
              Assign Icon
            </label>
            <div className="grid grid-cols-5 gap-2.5 p-3.5 bg-gray-50 rounded-2xl border border-gray-100 max-h-56 overflow-y-auto">
              {Object.keys(ICON_MAP).map((iconName) => {
                const IconComponent = ICON_MAP[iconName];
                const isSelected = selectedIcon === iconName;
                return (
                  <button
                    key={iconName}
                    type="button"
                    onClick={() => setSelectedIcon(iconName)}
                    title={iconName}
                    className={`flex items-center justify-center p-3 rounded-xl transition-all ${
                      isSelected 
                        ? 'bg-teal-600 text-white shadow-md shadow-teal-600/20 scale-105' 
                        : 'bg-white text-gray-600 hover:text-teal-600 hover:bg-teal-50 border border-gray-100'
                    }`}
                  >
                    <IconComponent className="h-5 w-5" />
                  </button>
                );
              })}
            </div>
          </div>

          {/* Preview Panel */}
          {name.trim() && (
            <div className="p-4 bg-teal-50/50 rounded-2xl border border-teal-100/50 flex items-center gap-3">
              <div className="p-2.5 bg-teal-600 text-white rounded-xl shadow-md shadow-teal-600/10">
                {React.createElement(ICON_MAP[selectedIcon] || HelpCircle, { className: 'h-5 w-5' })}
              </div>
              <div>
                <p className="text-[10px] font-bold text-teal-600 uppercase tracking-wider">Preview</p>
                <p className="text-sm font-semibold text-gray-800">{name}</p>
              </div>
            </div>
          )}

          <div className="flex gap-3 pt-2">
            {editingId && (
              <button
                type="button"
                onClick={handleCancel}
                className="flex-1 px-4 py-2.5 border border-gray-200 text-gray-600 font-bold rounded-xl hover:bg-gray-50 transition-all text-sm"
              >
                Cancel
              </button>
            )}
            <button
              type="submit"
              disabled={saving}
              className="flex-[2] bg-teal-600 hover:bg-teal-700 text-white font-bold py-2.5 px-4 rounded-xl shadow-lg shadow-teal-600/15 hover:shadow-xl hover:shadow-teal-600/20 transition-all disabled:opacity-50 text-sm flex items-center justify-center gap-1.5"
            >
              <Plus size={16} />
              {saving ? 'Saving...' : (editingId ? 'Update Category' : 'Create Category')}
            </button>
          </div>
        </form>
      </div>

      {/* Grid of existing Categories (Right/Main Column) */}
      <div className="flex-1 bg-white p-6 rounded-2xl border border-gray-100 shadow-sm flex flex-col min-h-[400px]">
        <div className="flex items-center justify-between mb-5 pb-2 border-b border-gray-100">
          <h3 className="text-lg font-bold text-gray-900">Manage Travel Categories</h3>
          <span className="text-xs text-gray-400 bg-gray-100 px-2.5 py-1 rounded-full font-semibold">
            {categories.length} Categories
          </span>
        </div>

        {loading ? (
          <div className="flex-1 flex flex-col items-center justify-center gap-2">
            <div className="w-8 h-8 border-4 border-teal-600 border-t-transparent rounded-full animate-spin" />
            <span className="text-sm font-medium text-gray-500">Loading categories...</span>
          </div>
        ) : categories.length === 0 ? (
          <div className="flex-1 flex flex-col items-center justify-center p-8 text-center bg-gray-50/50 rounded-2xl border border-dashed border-gray-200">
            <AlertCircle className="h-10 w-10 text-gray-300 mb-3" />
            <h4 className="text-sm font-bold text-gray-700">No categories found</h4>
            <p className="text-xs text-gray-400 mt-1 max-w-[240px]">Create travel categories on the left to start configuring options.</p>
          </div>
        ) : (
          <div className="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-3 gap-4">
            {categories.map((category) => {
              const IconComp = ICON_MAP[category.icon] || HelpCircle;
              return (
                <div 
                  key={category.id} 
                  className="flex items-center justify-between p-4 rounded-xl border border-gray-100 hover:border-teal-500 hover:shadow-md hover:shadow-teal-600/5 transition-all group bg-white"
                >
                  <div className="flex items-center gap-3.5">
                    <div className="p-3 bg-teal-50 text-teal-600 rounded-xl group-hover:bg-teal-600 group-hover:text-white transition-all shadow-sm">
                      <IconComp className="h-5 w-5" />
                    </div>
                    <div>
                      <span className="text-sm font-bold text-gray-800 leading-snug block">
                        {category.name}
                      </span>
                      <span className="text-[10px] text-gray-400 uppercase font-bold tracking-wider">
                        {category.icon || 'HelpCircle'} Icon
                      </span>
                    </div>
                  </div>

                  <div className="flex gap-1.5 opacity-0 group-hover:opacity-100 transition-opacity">
                    <button
                      onClick={() => handleEdit(category)}
                      className="p-2 text-teal-600 hover:bg-teal-50 rounded-lg transition-colors border border-transparent hover:border-teal-100"
                      title="Edit Category"
                    >
                      <Edit2 size={14} />
                    </button>
                    <button
                      onClick={() => handleDelete(category.id)}
                      className="p-2 text-red-500 hover:bg-red-50 rounded-lg transition-colors border border-transparent hover:border-red-100"
                      title="Delete Category"
                    >
                      <Trash2 size={14} />
                    </button>
                  </div>
                </div>
              );
            })}
          </div>
        )}
      </div>
    </div>
  );
}
