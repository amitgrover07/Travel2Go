import React, { useState, useEffect } from 'react';
import { 
  Hotel, Car, Compass, MapPin, Activity, Plane, Ship, Train, 
  Camera, Coffee, Utensils, Tent, Ticket, Shield, HelpCircle, 
  Plus, Edit2, Trash2, X, Search, ChevronDown, ChevronUp, 
  AlertCircle, IndianRupee, Save, Settings
} from 'lucide-react';
import toast from 'react-hot-toast';
import api from '../services/api';
import { formatCurrency } from '../utils/formatUtils';

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

const defaultConfigurationForm = {
  configCode: '',
  title: '',
  description: '',
  status: 'ACTIVE',
  targetType: 'NONE', // 'NONE' | 'DESTINATION' | 'PACKAGE'
  targetValue: '',
  options: []
};

const defaultOptionForm = {
  categoryName: '',
  optionName: '',
  basePrice: '',
  markupPercentage: 0,
  price: 0
};

export default function AdminConfigurator() {
  const [configurations, setConfigurations] = useState([]);
  const [categories, setCategories] = useState([]);
  const [packages, setPackages] = useState([]);
  const [loading, setLoading] = useState(true);
  const [formData, setFormData] = useState(defaultConfigurationForm);
  const [editingId, setEditingId] = useState(null);
  const [saving, setSaving] = useState(false);
  const [searchQuery, setSearchQuery] = useState('');

  // Option Form State
  const [optionData, setOptionData] = useState(defaultOptionForm);
  const [editingOptionIndex, setEditingOptionIndex] = useState(null);

  useEffect(() => {
    fetchData();
  }, []);

  const fetchData = async () => {
    try {
      setLoading(true);
      const [configsRes, catsRes, packagesRes] = await Promise.all([
        api.get('/configurators/all'),
        api.get('/configurator-categories'),
        api.get('/packages/all')
      ]);
      const configs = Array.isArray(configsRes.data) ? configsRes.data : [];
      setConfigurations(configs);
      setCategories(Array.isArray(catsRes.data) ? catsRes.data : []);
      setPackages(Array.isArray(packagesRes.data) ? packagesRes.data : []);
      
      setFormData(prev => ({
        ...prev,
        configCode: editingId ? prev.configCode : getNextConfigCode(configs)
      }));
    } catch (err) {
      console.error('Failed to load configurator data', err);
      toast.error('Failed to load configurator data');
    } finally {
      setLoading(false);
    }
  };

  const getNextConfigCode = (configsList) => {
    if (!configsList || configsList.length === 0) return 'CONF-001';
    let maxNumber = 0;
    configsList.forEach(cfg => {
      const match = cfg.configCode?.match(/^CONF-(\d+)$/i);
      if (match) {
        const num = parseInt(match[1], 10);
        if (num > maxNumber) maxNumber = num;
      }
    });
    return `CONF-${String(maxNumber + 1).padStart(3, '0')}`;
  };

  const handleTopLevelChange = (e) => {
    const { name, value } = e.target;
    setFormData(prev => ({ ...prev, [name]: value }));
  };

  // Option actions
  const handleAddOption = () => {
    if (!optionData.categoryName) {
      toast.error('Please select a category');
      return;
    }
    if (!optionData.optionName.trim()) {
      toast.error('Option details/name is required');
      return;
    }
    if (optionData.basePrice === '' || Number(optionData.basePrice) < 0) {
      toast.error('Please enter a valid base price');
      return;
    }

    const basePriceNum = Number(optionData.basePrice);
    const markupPct = optionData.markupPercentage === '' ? 0 : Number(optionData.markupPercentage);
    const finalPrice = Math.ceil(basePriceNum * (1 + markupPct / 100));

    const newOption = {
      categoryName: optionData.categoryName,
      optionName: optionData.optionName.trim(),
      basePrice: basePriceNum,
      markupPercentage: markupPct,
      price: finalPrice
    };

    if (editingOptionIndex !== null) {
      // Update
      const updatedOptions = [...formData.options];
      updatedOptions[editingOptionIndex] = newOption;
      setFormData(prev => ({ ...prev, options: updatedOptions }));
      setEditingOptionIndex(null);
      toast.success('Option updated in configuration');
    } else {
      // Add
      setFormData(prev => ({ ...prev, options: [...prev.options, newOption] }));
      toast.success('Option added to configuration');
    }

    setOptionData(defaultOptionForm);
  };

  const handleEditOption = (index) => {
    const opt = formData.options[index];
    setOptionData({
      categoryName: opt.categoryName,
      optionName: opt.optionName,
      basePrice: opt.basePrice !== undefined ? opt.basePrice : opt.price,
      markupPercentage: opt.markupPercentage !== undefined ? opt.markupPercentage : 0,
      price: opt.price
    });
    setEditingOptionIndex(index);
  };

  const handleDeleteOption = (index) => {
    const filtered = formData.options.filter((_, idx) => idx !== index);
    setFormData(prev => ({ ...prev, options: filtered }));
    if (editingOptionIndex === index) {
      setEditingOptionIndex(null);
      setOptionData(defaultOptionForm);
    }
    toast.success('Option removed');
  };

  // Submit configuration
  const handleSubmit = async (e) => {
    e.preventDefault();
    if (!formData.title.trim()) {
      toast.error('Configuration title is required');
      return;
    }
    if (formData.options.length === 0) {
      toast.error('Add at least one option under this configuration');
      return;
    }

    setSaving(true);
    const payload = {
      ...formData,
      title: formData.title.trim(),
      description: formData.description.trim()
    };

    try {
      if (editingId) {
        await api.put(`/configurators/${editingId}`, payload);
        toast.success('Configuration updated successfully');
      } else {
        await api.post('/configurators', payload);
        toast.success('Configuration saved successfully');
      }
      
      handleCancel();
      fetchData();
    } catch (err) {
      console.error('Failed to save configuration', err);
      if (err.response?.status === 409) {
        toast.error('Configuration code already exists');
      } else {
        toast.error(err.response?.data?.message || 'Failed to save configuration');
      }
    } finally {
      setSaving(false);
    }
  };

  const handleEditConfig = (cfg) => {
    setEditingId(cfg.id);
    setFormData({
      configCode: cfg.configCode,
      title: cfg.title,
      description: cfg.description || '',
      status: cfg.status || 'ACTIVE',
      targetType: cfg.targetType || 'NONE',
      targetValue: cfg.targetValue || '',
      options: cfg.options || []
    });
    setOptionData(defaultOptionForm);
    setEditingOptionIndex(null);
  };

  const handleDeleteConfig = async (id) => {
    if (!window.confirm('Are you sure you want to delete this configuration?')) return;
    try {
      await api.delete(`/configurators/${id}`);
      toast.success('Configuration deleted successfully');
      fetchData();
    } catch (err) {
      toast.error('Failed to delete configuration');
    }
  };

  const handleCancel = () => {
    setEditingId(null);
    setFormData({
      ...defaultConfigurationForm,
      configCode: getNextConfigCode(configurations)
    });
    setOptionData(defaultOptionForm);
    setEditingOptionIndex(null);
  };

  const totalCost = formData.options.reduce((sum, opt) => sum + opt.price, 0);

  const uniqueDestinations = Array.from(
    new Set(packages.map(p => p.destination).filter(Boolean))
  ).sort();

  const getCategoryIcon = (catName) => {
    const matched = categories.find(c => c.name === catName);
    return matched ? matched.icon : 'HelpCircle';
  };

  return (
    <div className="flex flex-col lg:flex-row gap-8">
      {/* Configuration Creator/Editor (Left/Main Column) */}
      <div className="lg:w-1/2 bg-white p-6 rounded-2xl border border-gray-100 shadow-sm overflow-y-auto max-h-[calc(100vh-8rem)]">
        <h2 className="text-xl font-bold text-gray-900 mb-5 pb-2 border-b border-gray-100 flex items-center gap-2">
          <Settings className="text-indigo-650" />
          {editingId ? 'Edit Configuration' : 'Create Configuration'}
        </h2>

        <form onSubmit={handleSubmit} className="space-y-6">
          {/* Basic Details */}
          <div className="space-y-4 bg-gray-50/50 p-4.5 rounded-2xl border border-gray-100">
            <h3 className="text-xs font-bold text-indigo-700 uppercase tracking-widest border-b pb-1.5 border-indigo-100">
              Basic Details
            </h3>
            <div className="grid grid-cols-2 gap-4">
              <div>
                <label className="block text-xs font-bold text-gray-500 uppercase tracking-wider mb-2 ml-1">
                  Config Code
                </label>
                <input
                  type="text"
                  name="configCode"
                  required
                  value={formData.configCode}
                  onChange={handleTopLevelChange}
                  className="w-full px-4 py-2.5 bg-white border border-gray-200 rounded-xl focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500 transition-all text-sm outline-none font-mono"
                />
              </div>
              <div>
                <label className="block text-xs font-bold text-gray-500 uppercase tracking-wider mb-2 ml-1">
                  Status
                </label>
                <select
                  name="status"
                  value={formData.status}
                  onChange={handleTopLevelChange}
                  className="w-full px-4 py-2.5 bg-white border border-gray-200 rounded-xl focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500 transition-all text-sm outline-none"
                >
                  <option value="ACTIVE">ACTIVE</option>
                  <option value="INACTIVE">INACTIVE</option>
                </select>
              </div>
            </div>
            <div>
              <label className="block text-xs font-bold text-gray-500 uppercase tracking-wider mb-2 ml-1">
                Title / Name
              </label>
              <input
                type="text"
                required
                name="title"
                value={formData.title}
                onChange={handleTopLevelChange}
                placeholder="e.g. Standard Couple Package Setup"
                className="w-full px-4 py-2.5 bg-white border border-gray-200 rounded-xl focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500 transition-all text-sm outline-none"
              />
            </div>
            <div>
              <label className="block text-xs font-bold text-gray-500 uppercase tracking-wider mb-2 ml-1">
                Description / Notes
              </label>
              <textarea
                name="description"
                value={formData.description}
                onChange={handleTopLevelChange}
                placeholder="Details about exclusions, duration, or specifications..."
                rows={3}
                className="w-full px-4 py-2.5 bg-white border border-gray-200 rounded-xl focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500 transition-all text-sm outline-none"
              />
            </div>

            {/* Attachment Rule */}
            <div className="border-t border-gray-200/60 pt-4 mt-2">
              <h4 className="text-xs font-bold text-indigo-700 uppercase tracking-widest mb-3">
                Attachment Rule
              </h4>
              <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                <div>
                  <label className="block text-xs font-bold text-gray-500 uppercase tracking-wider mb-2 ml-1">
                    Attach Via
                  </label>
                  <select
                    name="targetType"
                    value={formData.targetType}
                    onChange={(e) => {
                      setFormData(prev => ({ 
                        ...prev, 
                        targetType: e.target.value,
                        targetValue: '' // Reset value when changing type
                      }));
                    }}
                    className="w-full px-4 py-2.5 bg-white border border-gray-200 rounded-xl focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500 transition-all text-sm outline-none"
                  >
                    <option value="NONE">Do Not Attach Automatically</option>
                    <option value="DESTINATION">By Destination</option>
                    <option value="PACKAGE">By Specific Package</option>
                  </select>
                </div>

                {formData.targetType === 'DESTINATION' && (
                  <div>
                    <label className="block text-xs font-bold text-gray-500 uppercase tracking-wider mb-2 ml-1">
                      Select Destination
                    </label>
                    <select
                      name="targetValue"
                      value={formData.targetValue}
                      onChange={handleTopLevelChange}
                      required
                      className="w-full px-4 py-2.5 bg-white border border-gray-200 rounded-xl focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500 transition-all text-sm outline-none"
                    >
                      <option value="">-- Choose Destination --</option>
                      {uniqueDestinations.map(dest => (
                        <option key={dest} value={dest}>{dest}</option>
                      ))}
                    </select>
                  </div>
                )}

                {formData.targetType === 'PACKAGE' && (
                  <div>
                    <label className="block text-xs font-bold text-gray-500 uppercase tracking-wider mb-2 ml-1">
                      Select Specific Package
                    </label>
                    <select
                      name="targetValue"
                      value={formData.targetValue}
                      onChange={handleTopLevelChange}
                      required
                      className="w-full px-4 py-2.5 bg-white border border-gray-200 rounded-xl focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500 transition-all text-sm outline-none"
                    >
                      <option value="">-- Choose Package --</option>
                      {packages.map(pkg => (
                        <option key={pkg.id} value={pkg.id}>
                          {pkg.packageCode ? `[${pkg.packageCode}] ` : ''}{pkg.title}
                        </option>
                      ))}
                    </select>
                  </div>
                )}
              </div>
            </div>
          </div>

          {/* Options List / Editor */}
          <div className="space-y-5 bg-gray-50/50 p-4.5 rounded-2xl border border-gray-100">
            <h3 className="text-xs font-bold text-indigo-700 uppercase tracking-widest border-b pb-1.5 border-indigo-100 flex items-center justify-between">
              <span>Configuration Options</span>
              <span className="text-[10px] bg-indigo-100 text-indigo-700 px-2 py-0.5 rounded-full lowercase">
                {formData.options.length} options
              </span>
            </h3>

            {/* Sub-form to add/edit single Option */}
            <div className="bg-white p-4 rounded-xl border border-gray-100 space-y-4 shadow-sm">
              <h4 className="text-xs font-bold text-gray-700 flex items-center gap-1.5">
                <Plus size={14} className="text-indigo-650" />
                {editingOptionIndex !== null ? 'Edit Custom Option' : 'Add Custom Option'}
              </h4>
              
              <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
                <div>
                  <label className="block text-[11px] font-semibold text-gray-500 mb-1.5 ml-0.5">
                    Category *
                  </label>
                  <select
                    value={optionData.categoryName}
                    onChange={(e) => setOptionData({ ...optionData, categoryName: e.target.value })}
                    className="w-full px-3 py-2 bg-gray-50 border border-gray-200 rounded-lg text-sm outline-none"
                  >
                    <option value="">-- Choose Category --</option>
                    {categories.map(cat => (
                      <option key={cat.id} value={cat.name}>{cat.name}</option>
                    ))}
                  </select>
                </div>
                <div>
                  <label className="block text-[11px] font-semibold text-gray-500 mb-1.5 ml-0.5">
                    Base Price (INR) *
                  </label>
                  <div className="relative">
                    <span className="absolute left-3 top-1/2 -translate-y-1/2 text-gray-400 font-bold text-xs">₹</span>
                    <input
                      type="number"
                      value={optionData.basePrice}
                      onChange={(e) => {
                        const base = e.target.value;
                        const markup = optionData.markupPercentage || 0;
                        const final = base !== '' ? Math.ceil(Number(base) * (1 + Number(markup) / 100)) : 0;
                        setOptionData({ ...optionData, basePrice: base, price: final });
                      }}
                      placeholder="e.g. 5000"
                      className="w-full pl-7 pr-3 py-2 bg-gray-50 border border-gray-200 rounded-lg text-sm outline-none"
                    />
                  </div>
                </div>
                <div>
                  <label className="block text-[11px] font-semibold text-gray-500 mb-1.5 ml-0.5">
                    Increase Price (%)
                  </label>
                  <div className="relative">
                    <input
                      type="number"
                      value={optionData.markupPercentage}
                      onChange={(e) => {
                        const markup = e.target.value;
                        const base = optionData.basePrice || 0;
                        const final = base !== '' ? Math.ceil(Number(base) * (1 + Number(markup) / 100)) : 0;
                        setOptionData({ ...optionData, markupPercentage: markup, price: final });
                      }}
                      placeholder="e.g. 10"
                      className="w-full px-3 py-2 bg-gray-50 border border-gray-200 rounded-lg text-sm outline-none pr-8"
                    />
                    <span className="absolute right-3 top-1/2 -translate-y-1/2 text-gray-400 font-bold text-xs">%</span>
                  </div>
                </div>
                <div className="md:col-span-3">
                  <label className="block text-[11px] font-semibold text-gray-500 mb-1.5 ml-0.5">
                    Option / Item Details *
                  </label>
                  <input
                    type="text"
                    value={optionData.optionName}
                    onChange={(e) => setOptionData({ ...optionData, optionName: e.target.value })}
                    placeholder="e.g. 3 Nights Stay at Munnar Resort (Standard Double Room)"
                    className="w-full px-3 py-2 bg-gray-50 border border-gray-200 rounded-lg text-sm outline-none"
                  />
                </div>
              </div>

              {optionData.basePrice !== '' && (
                <div className="p-3 bg-indigo-50 border border-indigo-100 rounded-lg flex justify-between items-center text-xs">
                  <span className="text-gray-500 font-medium">Final Option Price (with markup):</span>
                  <span className="font-extrabold text-indigo-700">₹{formatCurrency(optionData.price)} INR</span>
                </div>
              )}

              <div className="flex justify-end gap-2 pt-1 border-t border-gray-50">
                {editingOptionIndex !== null && (
                  <button
                    type="button"
                    onClick={() => { setOptionData(defaultOptionForm); setEditingOptionIndex(null); }}
                    className="px-3.5 py-1.5 bg-gray-100 hover:bg-gray-200 text-gray-700 text-xs font-bold rounded-lg transition-all"
                  >
                    Cancel
                  </button>
                )}
                <button
                  type="button"
                  onClick={handleAddOption}
                  className="px-4 py-1.5 bg-indigo-600 hover:bg-indigo-700 text-white text-xs font-bold rounded-lg transition-all flex items-center gap-1 shadow-md shadow-indigo-600/10 hover:shadow-lg"
                >
                  {editingOptionIndex !== null ? 'Update Option' : 'Add Option'}
                </button>
              </div>
            </div>

            {/* List of currently created options */}
            {formData.options.length === 0 ? (
              <div className="p-6 bg-white rounded-xl border border-gray-200/50 text-center flex flex-col items-center gap-2">
                <AlertCircle className="h-8 w-8 text-gray-300" />
                <p className="text-xs font-bold text-gray-500">No options configured yet</p>
                <p className="text-[10px] text-gray-400">Add categories, options, and pricing above to build your configuration.</p>
              </div>
            ) : (
              <div className="space-y-2 max-h-80 overflow-y-auto pr-1">
                {formData.options.map((opt, idx) => {
                  const iconName = getCategoryIcon(opt.categoryName);
                  const IconComponent = ICON_MAP[iconName] || HelpCircle;
                  return (
                    <div 
                      key={idx} 
                      className="flex items-center justify-between p-3.5 bg-white border border-gray-100 rounded-xl hover:border-indigo-300 transition-all shadow-sm group"
                    >
                      <div className="flex items-center gap-3.5 min-w-0 flex-1">
                        <div className="p-2.5 bg-indigo-50 text-indigo-600 rounded-lg shadow-sm">
                          <IconComponent className="h-4.5 w-4.5" />
                        </div>
                        <div className="min-w-0 flex-1 pr-3">
                          <div className="flex items-center gap-2 mb-0.5">
                            <span className="text-[10px] font-bold uppercase tracking-wider text-indigo-700 bg-indigo-50 px-2 py-0.5 rounded-md">
                              {opt.categoryName}
                            </span>
                            <span className="text-[10px] font-semibold text-gray-500">
                              Option #{idx + 1}
                            </span>
                          </div>
                          <p className="text-xs font-bold text-gray-700 truncate leading-snug">
                            {opt.optionName}
                          </p>
                        </div>
                      </div>

                      <div className="flex items-center gap-3.5 shrink-0">
                        <div className="text-right">
                          <span className="text-xs font-extrabold text-indigo-650 block">
                            ₹{formatCurrency(opt.price)}
                          </span>
                          <span className="text-[9px] font-semibold text-gray-400">
                            INR
                          </span>
                        </div>
                        
                        <div className="flex gap-1">
                          <button
                            type="button"
                            onClick={() => handleEditOption(idx)}
                            className="p-1.5 text-blue-500 hover:bg-blue-50 rounded border border-transparent hover:border-blue-100 transition-colors"
                            title="Edit Option"
                          >
                            <Edit2 size={13} />
                          </button>
                          <button
                            type="button"
                            onClick={() => handleDeleteOption(idx)}
                            className="p-1.5 text-red-400 hover:bg-red-50 rounded border border-transparent hover:border-red-100 transition-colors"
                            title="Remove Option"
                          >
                            <X size={13} />
                          </button>
                        </div>
                      </div>
                    </div>
                  );
                })}

                {/* Total Cost Summary */}
                <div className="p-4 bg-indigo-50 border border-indigo-100 rounded-xl flex items-center justify-between mt-3 shadow-inner">
                  <div>
                    <p className="text-[10px] font-extrabold text-indigo-600 uppercase tracking-widest">Total Configuration Pricing</p>
                    <p className="text-xs text-gray-500 mt-0.5">Sum of all individual items</p>
                  </div>
                  <div className="text-right">
                    <span className="text-lg font-black text-indigo-800 flex items-center gap-0.5 justify-end">
                      ₹{formatCurrency(totalCost)}
                    </span>
                    <span className="text-[9px] font-bold text-indigo-600 uppercase tracking-wider">INR</span>
                  </div>
                </div>
              </div>
            )}
          </div>

          <div className="flex justify-end gap-3 border-t pt-5">
            {editingId && (
              <button
                type="button"
                onClick={handleCancel}
                className="px-5 py-2.5 border border-gray-200 text-gray-600 font-bold rounded-xl bg-white hover:bg-gray-50 text-sm transition-all shadow-sm"
              >
                Cancel
              </button>
            )}
            <button
              type="submit"
              disabled={saving}
              className="flex items-center gap-1.5 px-7 py-2.5 bg-indigo-600 hover:bg-indigo-700 text-white font-bold rounded-xl shadow-lg shadow-indigo-600/15 hover:shadow-xl hover:shadow-indigo-600/20 text-sm transition-all disabled:bg-indigo-400"
            >
              <Save size={16} />
              {saving ? 'Saving...' : (editingId ? 'Update Configuration' : 'Save Configuration')}
            </button>
          </div>
        </form>
      </div>

      {/* Configuration List Section (Right Column) */}
      <div className="lg:w-1/2 bg-white rounded-2xl border border-gray-100 shadow-sm overflow-hidden flex flex-col h-[calc(100vh-8rem)]">
        <div className="p-4.5 border-b bg-gray-50/50 flex items-center justify-between">
          <div>
            <h2 className="text-lg font-bold text-gray-900 leading-snug">Configured Setups</h2>
            <p className="text-[10px] text-gray-400 font-semibold mt-0.5">List of travel configurators</p>
          </div>
          <div className="relative w-56">
            <Search size={14} className="absolute left-3 top-1/2 -translate-y-1/2 text-gray-400" />
            <input
              type="text"
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              placeholder="Search setups..."
              className="w-full pl-9 pr-3.5 py-1.5 text-xs bg-white border border-gray-200 rounded-xl outline-none focus:ring-2 focus:ring-indigo-500 transition-all font-semibold"
            />
          </div>
        </div>

        <div className="overflow-y-auto flex-1 p-5 space-y-4">
          {loading ? (
            <div className="h-full flex flex-col items-center justify-center gap-2">
              <div className="w-8 h-8 border-4 border-indigo-600 border-t-transparent rounded-full animate-spin" />
              <span className="text-xs font-semibold text-gray-400">Loading configurations...</span>
            </div>
          ) : configurations.length === 0 ? (
            <div className="h-full flex flex-col items-center justify-center p-8 text-center bg-gray-50/50 border border-dashed rounded-2xl border-gray-200">
              <AlertCircle className="h-10 w-10 text-gray-300 mb-3" />
              <h4 className="text-sm font-bold text-gray-750">No configurations found</h4>
              <p className="text-xs text-gray-400 mt-1 max-w-[250px]">Create configurations on the left using travel categories.</p>
            </div>
          ) : (
            configurations
              .filter(cfg => 
                cfg.configCode?.toLowerCase().includes(searchQuery.toLowerCase()) || 
                cfg.title?.toLowerCase().includes(searchQuery.toLowerCase())
              )
              .map((cfg) => {
                const configSum = (cfg.options || []).reduce((s, o) => s + o.price, 0);
                return (
                  <div 
                    key={cfg.id} 
                    className="group relative flex flex-col md:flex-row gap-4 justify-between items-start md:items-center rounded-2xl border border-gray-200 bg-white p-4.5 hover:border-indigo-500 hover:shadow-lg transition-all"
                  >
                    <div className="min-w-0 flex-1">
                      <div className="flex items-center gap-2 mb-1.5">
                        <span className="text-[10px] font-mono font-black bg-indigo-55 text-indigo-700 px-2 py-0.5 rounded-md">
                          {cfg.configCode}
                        </span>
                        <span className={`text-[9px] font-extrabold px-2 py-0.5 rounded-full ${
                          cfg.status === 'ACTIVE' ? 'bg-green-100 text-green-700' : 'bg-red-100 text-red-705'
                        }`}>
                          {cfg.status}
                        </span>
                        <span className="text-[9px] font-extrabold bg-gray-100 text-gray-500 px-2 py-0.5 rounded-full">
                          {(cfg.options || []).length} Options
                        </span>
                        {cfg.targetType && cfg.targetType !== 'NONE' && (
                          <span className="text-[9px] font-extrabold bg-indigo-50 text-indigo-700 px-2 py-0.5 rounded-full">
                            Attached: {cfg.targetType === 'DESTINATION' ? `Dest: ${cfg.targetValue}` : `Pkg: ${packages.find(p => p.id === cfg.targetValue)?.packageCode || cfg.targetValue}`}
                          </span>
                        )}
                      </div>
                      
                      <h3 className="font-extrabold text-gray-900 text-sm truncate group-hover:text-indigo-700 transition-colors">
                        {cfg.title}
                      </h3>
                      {cfg.description && (
                        <p className="text-[11px] text-gray-400 mt-0.5 truncate max-w-[340px]">
                          {cfg.description}
                        </p>
                      )}
                      
                      {/* Price Tag */}
                      <div className="mt-2 text-xs font-black text-indigo-650 flex items-center gap-0.5">
                        <span className="text-[10px] font-bold text-gray-400">Total Setup:</span>
                        ₹{formatCurrency(configSum)} INR
                      </div>
                    </div>

                    <div className="flex gap-2 w-full md:w-auto justify-end border-t md:border-t-0 pt-3 md:pt-0 mt-2.5 md:mt-0 shrink-0">
                      <button 
                        onClick={() => handleEditConfig(cfg)} 
                        className="text-blue-500 hover:bg-blue-50 p-2 rounded-xl transition-all border border-transparent hover:border-blue-100"
                        title="Edit Setup"
                      >
                        <Edit2 size={15} />
                      </button>
                      <button 
                        onClick={() => handleDeleteConfig(cfg.id)} 
                        className="text-red-500 hover:bg-red-50 p-2 rounded-xl transition-all border border-transparent hover:border-red-100"
                        title="Delete Setup"
                      >
                        <Trash2 size={15} />
                      </button>
                    </div>
                  </div>
                );
              })
          )}
        </div>
      </div>
    </div>
  );
}
