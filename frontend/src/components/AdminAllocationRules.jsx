import React, { useState, useEffect } from 'react';
import { 
  Plus, Edit2, Trash2, X, Search, ChevronDown, ChevronUp, 
  AlertCircle, IndianRupee, Save, Settings, Info, Package,
  Sliders, Calendar, User, Users, CheckSquare, Square, Car,
  Hotel, Compass, MapPin, Activity, Plane, Ship, Train, Camera, Coffee,
  Utensils, Tent, Ticket, Shield, HelpCircle, Bus, Luggage, Palmtree,
  Mountain, Sunset, Globe, Map, Wifi, Wine, Briefcase, Clock, Sparkles,
  Heart, Sun, Umbrella, Key, Bike, Tag, Footprints
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
  Bus,
  Luggage,
  Palmtree,
  Mountain,
  Sunset,
  Globe,
  Map,
  Wifi,
  Wine,
  Briefcase,
  Calendar,
  Clock,
  Sparkles,
  Heart,
  Sun,
  Umbrella,
  Key,
  Bike,
  Tag,
  Footprints,
  HelpCircle
};

const defaultRuleForm = {
  ruleCode: '',
  title: '',
  description: '',
  status: 'ACTIVE',
  baseRoomRate: 3000,
  extraAdultRate: 1200,
  cwbRate: 800,
  cnbRate: 400,
  sightseeingTicketPrice: 500,
  packageType: 'FIT',
  mealPlan: 'CP',
  includeSightseeing: true,
  vehicles: [
    { vehicleName: 'Sedan', maxPax: 4, dailyRate: 2000, tollCharges: 500, permitTax: 0, driverAllowance: 300 },
    { vehicleName: 'SUV', maxPax: 6, dailyRate: 2500, tollCharges: 600, permitTax: 0, driverAllowance: 300 },
    { vehicleName: 'Innova Crysta', maxPax: 6, dailyRate: 3000, tollCharges: 800, permitTax: 0, driverAllowance: 300 },
    { vehicleName: 'Tempo Traveller', maxPax: 12, dailyRate: 5000, tollCharges: 1500, permitTax: 500, driverAllowance: 500 }
  ],
  mappedPackageIds: [],
  customActivities: []
};

const defaultVehicleRow = {
  vehicleName: '',
  maxPax: 4,
  dailyRate: 0,
  tollCharges: 0,
  permitTax: 0,
  driverAllowance: 0
};

export default function AdminAllocationRules() {
  const [rules, setRules] = useState([]);
  const [packages, setPackages] = useState([]);
  const [categories, setCategories] = useState([]);
  const [loading, setLoading] = useState(true);
  const [formData, setFormData] = useState(defaultRuleForm);
  const [editingId, setEditingId] = useState(null);
  const [saving, setSaving] = useState(false);
  const [searchQuery, setSearchQuery] = useState('');
  const [showForm, setShowForm] = useState(false);
  const [packageSearch, setPackageSearch] = useState('');

  // Option Form State for Custom Activities
  const [optionData, setOptionData] = useState({
    categoryName: '',
    optionName: '',
    basePrice: '',
    markupPercentage: 0,
    price: 0
  });
  const [editingOptionIndex, setEditingOptionIndex] = useState(null);

  useEffect(() => {
    fetchData();
  }, []);

  const fetchData = async () => {
    try {
      setLoading(true);
      const [rulesRes, packagesRes, categoriesRes] = await Promise.all([
        api.get('/allocation-rules/all'),
        api.get('/packages/all'),
        api.get('/configurator-categories')
      ]);
      setRules(Array.isArray(rulesRes.data) ? rulesRes.data : []);
      setPackages(Array.isArray(packagesRes.data) ? packagesRes.data : []);
      setCategories(Array.isArray(categoriesRes.data) ? categoriesRes.data : []);
    } catch (err) {
      console.error('Failed to load rules data', err);
      toast.error('Failed to load rules data');
    } finally {
      setLoading(false);
    }
  };

  const getNextRuleCode = (rulesList) => {
    if (!rulesList || rulesList.length === 0) return 'RULE-001';
    let maxNumber = 0;
    rulesList.forEach(r => {
      const match = r.ruleCode?.match(/^RULE-(\d+)$/i);
      if (match) {
        const num = parseInt(match[1], 10);
        if (num > maxNumber) maxNumber = num;
      }
    });
    return `RULE-${String(maxNumber + 1).padStart(3, '0')}`;
  };

  const handleCreateNew = () => {
    const nextCode = getNextRuleCode(rules);
    setFormData({
      ...defaultRuleForm,
      ruleCode: nextCode,
      mappedPackageIds: [],
      customActivities: []
    });
    setEditingId(null);
    setOptionData({
      categoryName: '',
      optionName: '',
      basePrice: '',
      markupPercentage: 0,
      price: 0
    });
    setEditingOptionIndex(null);
    setShowForm(true);
  };

  const handleEdit = (rule) => {
    setFormData({
      ...defaultRuleForm,
      ...rule,
      vehicles: Array.isArray(rule.vehicles) ? rule.vehicles : [],
      mappedPackageIds: Array.isArray(rule.mappedPackageIds) ? rule.mappedPackageIds : [],
      customActivities: Array.isArray(rule.customActivities) ? rule.customActivities : []
    });
    setEditingId(rule.id);
    setOptionData({
      categoryName: '',
      optionName: '',
      basePrice: '',
      markupPercentage: 0,
      price: 0
    });
    setEditingOptionIndex(null);
    setShowForm(true);
  };

  const handleDelete = async (id) => {
    if (!window.confirm('Are you sure you want to delete this allocation rule?')) return;
    try {
      await api.delete(`/allocation-rules/${id}`);
      toast.success('Allocation rule deleted successfully');
      fetchData();
    } catch (err) {
      console.error('Delete error:', err);
      toast.error(err.response?.data?.message || 'Failed to delete rule');
    }
  };

  const handleAddCustomActivity = () => {
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

    const updatedOptions = [...(formData.customActivities || [])];
    if (editingOptionIndex !== null) {
      updatedOptions[editingOptionIndex] = newOption;
      setEditingOptionIndex(null);
      toast.success('Option updated in configuration');
    } else {
      updatedOptions.push(newOption);
      toast.success('Option added to configuration');
    }

    setFormData(prev => ({ ...prev, customActivities: updatedOptions }));
    setOptionData({
      categoryName: '',
      optionName: '',
      basePrice: '',
      markupPercentage: 0,
      price: 0
    });
  };

  const handleEditCustomActivity = (index) => {
    const opt = formData.customActivities[index];
    setOptionData({
      categoryName: opt.categoryName,
      optionName: opt.optionName,
      basePrice: opt.basePrice !== undefined ? opt.basePrice : opt.price,
      markupPercentage: opt.markupPercentage !== undefined ? opt.markupPercentage : 0,
      price: opt.price
    });
    setEditingOptionIndex(index);
  };

  const handleDeleteCustomActivity = (index) => {
    const filtered = formData.customActivities.filter((_, idx) => idx !== index);
    setFormData(prev => ({ ...prev, customActivities: filtered }));
    if (editingOptionIndex === index) {
      setEditingOptionIndex(null);
      setOptionData({
        categoryName: '',
        optionName: '',
        basePrice: '',
        markupPercentage: 0,
        price: 0
      });
    }
    toast.success('Option removed');
  };

  const handleFormChange = (e) => {
    const { name, value } = e.target;
    setFormData(prev => ({
      ...prev,
      [name]: value
    }));
  };

  const handleNumberChange = (e) => {
    const { name, value } = e.target;
    setFormData(prev => ({
      ...prev,
      [name]: Number(value) || 0
    }));
  };

  // Vehicles list updates
  const handleVehicleChange = (index, field, value) => {
    const updatedVehicles = [...formData.vehicles];
    if (field === 'vehicleName') {
      updatedVehicles[index][field] = value;
    } else {
      updatedVehicles[index][field] = Number(value) || 0;
    }
    setFormData(prev => ({ ...prev, vehicles: updatedVehicles }));
  };

  const addVehicleRow = () => {
    setFormData(prev => ({
      ...prev,
      vehicles: [...prev.vehicles, { ...defaultVehicleRow }]
    }));
  };

  const removeVehicleRow = (index) => {
    const updatedVehicles = formData.vehicles.filter((_, i) => i !== index);
    setFormData(prev => ({ ...prev, vehicles: updatedVehicles }));
  };

  // Packages mapping updates
  const togglePackageSelection = (pkgId) => {
    const currentSelection = [...formData.mappedPackageIds];
    const index = currentSelection.indexOf(pkgId);
    if (index > -1) {
      currentSelection.splice(index, 1);
    } else {
      currentSelection.push(pkgId);
    }
    setFormData(prev => ({ ...prev, mappedPackageIds: currentSelection }));
  };

  const selectAllFilteredPackages = (filtered) => {
    const current = [...formData.mappedPackageIds];
    filtered.forEach(p => {
      if (!current.includes(p.id)) {
        current.push(p.id);
      }
    });
    setFormData(prev => ({ ...prev, mappedPackageIds: current }));
  };

  const deselectAllFilteredPackages = (filtered) => {
    const filteredIds = filtered.map(p => p.id);
    const updated = formData.mappedPackageIds.filter(id => !filteredIds.includes(id));
    setFormData(prev => ({ ...prev, mappedPackageIds: updated }));
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (!formData.ruleCode.trim()) {
      toast.error('Rule code is required');
      return;
    }
    if (!formData.title.trim()) {
      toast.error('Title is required');
      return;
    }

    setSaving(true);
    try {
      if (editingId) {
        await api.put(`/allocation-rules/${editingId}`, formData);
        toast.success('Allocation rule updated successfully');
      } else {
        await api.post('/allocation-rules', formData);
        toast.success('Allocation rule created successfully');
      }
      setShowForm(false);
      fetchData();
    } catch (err) {
      console.error('Submit error:', err);
      toast.error(err.response?.data?.message || 'Failed to save rule. Ensure code is unique.');
    } finally {
      setSaving(false);
    }
  };

  // Filters
  const filteredRules = rules.filter(r => 
    (r.ruleCode || '').toLowerCase().includes(searchQuery.toLowerCase()) ||
    (r.title || '').toLowerCase().includes(searchQuery.toLowerCase()) ||
    (r.description || '').toLowerCase().includes(searchQuery.toLowerCase())
  );

  const filteredPackages = packages.filter(p => 
    (p.title || '').toLowerCase().includes(packageSearch.toLowerCase()) ||
    (p.packageCode || '').toLowerCase().includes(packageSearch.toLowerCase()) ||
    (p.destination || '').toLowerCase().includes(packageSearch.toLowerCase())
  );

  if (loading) {
    return (
      <div className="flex justify-center items-center py-24">
        <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-indigo-600"></div>
      </div>
    );
  }

  return (
    <div className="space-y-6">
      {/* Header Panel */}
      <div className="flex flex-col sm:flex-row justify-between sm:items-center gap-4 bg-white p-6 rounded-2xl border border-gray-100 shadow-sm">
        <div>
          <h1 className="text-xl font-extrabold text-gray-900 tracking-tight flex items-center gap-2">
            <Sliders className="h-6 w-6 text-indigo-600" />
            Hotel & Transport Allocation Rules
          </h1>
          <p className="text-xs text-gray-400 font-semibold mt-1">
            Configure dynamic rules, rates, vehicle pricing sheets, and map them to holiday packages.
          </p>
        </div>
        {!showForm && (
          <button 
            onClick={handleCreateNew}
            className="bg-indigo-600 hover:bg-indigo-700 text-white font-bold py-2.5 px-4 rounded-xl shadow-md hover:shadow-lg transition-all flex items-center gap-2 text-sm self-start sm:self-center"
          >
            <Plus size={18} />
            Create New Rule
          </button>
        )}
      </div>

      {/* Main Form View */}
      {showForm ? (
        <form onSubmit={handleSubmit} className="bg-white rounded-2xl border border-gray-100 shadow-md overflow-hidden animate-in fade-in slide-in-from-bottom-2 duration-200">
          <div className="p-6 border-b border-gray-100 bg-gray-50/50 flex justify-between items-center">
            <div>
              <h2 className="text-lg font-bold text-gray-900">
                {editingId ? `Edit Rule: ${formData.ruleCode}` : 'Create Allocation Rule'}
              </h2>
              <p className="text-xs text-gray-400 mt-0.5">Fill in hotel sharing costs, ticket costs, and vehicle configurations.</p>
            </div>
            <button 
              type="button" 
              onClick={() => setShowForm(false)}
              className="p-2 hover:bg-gray-100 rounded-lg text-gray-500 transition-colors"
            >
              <X size={20} />
            </button>
          </div>

          <div className="p-6 space-y-8">
            {/* Row 1: Basic Information */}
            <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
              <div>
                <label className="block text-xs font-bold text-gray-500 uppercase tracking-wider mb-2">Rule Code *</label>
                <input 
                  type="text" 
                  name="ruleCode"
                  required
                  placeholder="e.g. RULE-001"
                  className="w-full px-4 py-2.5 bg-gray-50 border border-gray-200 rounded-xl focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500 transition-all font-semibold uppercase"
                  value={formData.ruleCode}
                  onChange={(e) => setFormData(prev => ({ ...prev, ruleCode: e.target.value.toUpperCase() }))}
                  disabled={!!editingId}
                />
              </div>
              <div className="md:col-span-2">
                <label className="block text-xs font-bold text-gray-500 uppercase tracking-wider mb-2">Rule Title/Name *</label>
                <input 
                  type="text" 
                  name="title"
                  required
                  placeholder="e.g. Standard Kerala Premium Allocation"
                  className="w-full px-4 py-2.5 bg-gray-50 border border-gray-200 rounded-xl focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500 transition-all font-semibold"
                  value={formData.title}
                  onChange={handleFormChange}
                />
              </div>
              <div className="md:col-span-2">
                <label className="block text-xs font-bold text-gray-500 uppercase tracking-wider mb-2">Description</label>
                <input 
                  type="text" 
                  name="description"
                  placeholder="Describe the target hotels standard or season rates..."
                  className="w-full px-4 py-2.5 bg-gray-50 border border-gray-200 rounded-xl focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500 transition-all text-sm font-medium"
                  value={formData.description}
                  onChange={handleFormChange}
                />
              </div>
              <div>
                <label className="block text-xs font-bold text-gray-500 uppercase tracking-wider mb-2">Status</label>
                <select 
                  name="status"
                  className="w-full px-4 py-2.5 bg-gray-50 border border-gray-200 rounded-xl focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500 transition-all font-bold text-sm"
                  value={formData.status}
                  onChange={handleFormChange}
                >
                  <option value="ACTIVE">ACTIVE</option>
                  <option value="INACTIVE">INACTIVE</option>
                </select>
              </div>
              
              <div>
                <label className="block text-xs font-bold text-gray-500 uppercase tracking-wider mb-2">Package Type (GIT vs FIT)</label>
                <select 
                  name="packageType"
                  className="w-full px-4 py-2.5 bg-gray-50 border border-gray-200 rounded-xl focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500 transition-all font-bold text-sm"
                  value={formData.packageType || 'FIT'}
                  onChange={handleFormChange}
                >
                  <option value="FIT">Free Independent Tour (FIT - Custom / Private)</option>
                  <option value="GIT">Group Inclusive Tour (GIT - Fixed / Group)</option>
                </select>
              </div>

              <div>
                <label className="block text-xs font-bold text-gray-500 uppercase tracking-wider mb-2">Meal Plan</label>
                <select 
                  name="mealPlan"
                  className="w-full px-4 py-2.5 bg-gray-50 border border-gray-200 rounded-xl focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500 transition-all font-bold text-sm"
                  value={formData.mealPlan || 'CP'}
                  onChange={handleFormChange}
                >
                  <option value="CP">CP (Continental Plan - Room + Breakfast)</option>
                  <option value="MAP">MAP (Modified American Plan - Room + Breakfast + Dinner)</option>
                  <option value="AP">AP (American Plan - Room + All Meals)</option>
                </select>
              </div>

              <div>
                <label className="block text-xs font-bold text-gray-500 uppercase tracking-wider mb-2">Include Sightseeing Upfront</label>
                <select 
                  name="includeSightseeing"
                  className="w-full px-4 py-2.5 bg-gray-50 border border-gray-200 rounded-xl focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500 transition-all font-bold text-sm"
                  value={formData.includeSightseeing === false ? 'false' : 'true'}
                  onChange={(e) => setFormData(prev => ({ ...prev, includeSightseeing: e.target.value === 'true' }))}
                >
                  <option value="true">Yes (Included in package base quote)</option>
                  <option value="false">No (Excluded from package base quote)</option>
                </select>
              </div>
            </div>

            {/* Row 2: Hotel Rates Configuration */}
            <div className="border-t border-gray-100 pt-6">
              <h3 className="text-sm font-bold text-indigo-600 uppercase tracking-wider mb-4 flex items-center gap-2">
                <Info size={16} /> Hotel Pricing & Sightseeing Rates
              </h3>
              <div className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-5 gap-6">
                <div>
                  <label className="block text-xs font-bold text-gray-500 uppercase tracking-wider mb-2">Base Room Rate (Double)</label>
                  <div className="relative">
                    <span className="absolute left-3 top-1/2 -translate-y-1/2 text-gray-400 font-bold text-sm">₹</span>
                    <input 
                      type="number" 
                      name="baseRoomRate"
                      required
                      min="0"
                      className="w-full pl-7 pr-3 py-2.5 bg-gray-50 border border-gray-200 rounded-xl focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500 font-bold"
                      value={formData.baseRoomRate}
                      onChange={handleNumberChange}
                    />
                  </div>
                </div>
                <div>
                  <label className="block text-xs font-bold text-gray-500 uppercase tracking-wider mb-2">Extra Adult Rate</label>
                  <div className="relative">
                    <span className="absolute left-3 top-1/2 -translate-y-1/2 text-gray-400 font-bold text-sm">₹</span>
                    <input 
                      type="number" 
                      name="extraAdultRate"
                      required
                      min="0"
                      className="w-full pl-7 pr-3 py-2.5 bg-gray-50 border border-gray-200 rounded-xl focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500 font-bold"
                      value={formData.extraAdultRate}
                      onChange={handleNumberChange}
                    />
                  </div>
                </div>
                <div>
                  <label className="block text-xs font-bold text-gray-500 uppercase tracking-wider mb-2">Child With Bed Rate (CWB)</label>
                  <div className="relative">
                    <span className="absolute left-3 top-1/2 -translate-y-1/2 text-gray-400 font-bold text-sm">₹</span>
                    <input 
                      type="number" 
                      name="cwbRate"
                      required
                      min="0"
                      className="w-full pl-7 pr-3 py-2.5 bg-gray-50 border border-gray-200 rounded-xl focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500 font-bold"
                      value={formData.cwbRate}
                      onChange={handleNumberChange}
                    />
                  </div>
                </div>
                <div>
                  <label className="block text-xs font-bold text-gray-500 uppercase tracking-wider mb-2">Child No Bed Rate (CNB)</label>
                  <div className="relative">
                    <span className="absolute left-3 top-1/2 -translate-y-1/2 text-gray-400 font-bold text-sm">₹</span>
                    <input 
                      type="number" 
                      name="cnbRate"
                      required
                      min="0"
                      className="w-full pl-7 pr-3 py-2.5 bg-gray-50 border border-gray-200 rounded-xl focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500 font-bold"
                      value={formData.cnbRate}
                      onChange={handleNumberChange}
                    />
                  </div>
                </div>
                <div>
                  <label className="block text-xs font-bold text-gray-500 uppercase tracking-wider mb-2">Sightseeing Ticket Price</label>
                  <div className="relative">
                    <span className="absolute left-3 top-1/2 -translate-y-1/2 text-gray-400 font-bold text-sm">₹</span>
                    <input 
                      type="number" 
                      name="sightseeingTicketPrice"
                      required
                      min="0"
                      className="w-full pl-7 pr-3 py-2.5 bg-gray-50 border border-gray-200 rounded-xl focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500 font-bold"
                      value={formData.sightseeingTicketPrice}
                      onChange={handleNumberChange}
                    />
                  </div>
                </div>
              </div>
            </div>

            {/* Row 3: Vehicle Rates Matrix */}
            <div className="border-t border-gray-100 pt-6">
              <div className="flex justify-between items-center mb-4">
                <h3 className="text-sm font-bold text-indigo-600 uppercase tracking-wider flex items-center gap-2">
                  <Car size={16} /> Vehicle Rates Matrix
                </h3>
                <button 
                  type="button" 
                  onClick={addVehicleRow}
                  className="text-xs bg-indigo-50 hover:bg-indigo-100 text-indigo-700 font-bold py-1.5 px-3 rounded-lg border border-indigo-100 flex items-center gap-1 transition-all"
                >
                  <Plus size={14} /> Add Vehicle Row
                </button>
              </div>

              {formData.vehicles.length === 0 ? (
                <div className="text-center py-6 border border-dashed border-gray-200 rounded-xl">
                  <p className="text-xs text-gray-400 font-bold">No vehicles defined. Add a vehicle row to map transport costs.</p>
                </div>
              ) : (
                <div className="overflow-x-auto rounded-xl border border-gray-200 shadow-sm">
                  <table className="min-w-full divide-y divide-gray-200 text-sm">
                    <thead className="bg-gray-50">
                      <tr>
                        <th className="px-4 py-3 text-left text-xs font-bold text-gray-500 uppercase tracking-wider w-1/4">Vehicle Name</th>
                        <th className="px-4 py-3 text-left text-xs font-bold text-gray-500 uppercase tracking-wider w-12">Max Pax</th>
                        <th className="px-4 py-3 text-left text-xs font-bold text-gray-500 uppercase tracking-wider">Daily Rate (₹)</th>
                        <th className="px-4 py-3 text-left text-xs font-bold text-gray-500 uppercase tracking-wider">Driver Night Allowance (₹)</th>
                        <th className="px-4 py-3 text-left text-xs font-bold text-gray-500 uppercase tracking-wider">Toll/Parking (₹)</th>
                        <th className="px-4 py-3 text-left text-xs font-bold text-gray-500 uppercase tracking-wider">Permit Tax (₹)</th>
                        <th className="px-4 py-3 text-center text-xs font-bold text-gray-500 uppercase tracking-wider w-10">Remove</th>
                      </tr>
                    </thead>
                    <tbody className="bg-white divide-y divide-gray-200">
                      {formData.vehicles.map((v, index) => (
                        <tr key={index}>
                          <td className="px-4 py-2">
                            <input 
                              type="text" 
                              required
                              placeholder="e.g. Sedan (Dzire)"
                              className="w-full px-2.5 py-1.5 border border-gray-200 rounded-lg text-sm font-semibold"
                              value={v.vehicleName}
                              onChange={(e) => handleVehicleChange(index, 'vehicleName', e.target.value)}
                            />
                          </td>
                          <td className="px-4 py-2">
                            <input 
                              type="number" 
                              required
                              min="1"
                              className="w-16 px-2.5 py-1.5 border border-gray-200 rounded-lg text-sm font-bold text-center"
                              value={v.maxPax}
                              onChange={(e) => handleVehicleChange(index, 'maxPax', e.target.value)}
                            />
                          </td>
                          <td className="px-4 py-2">
                            <input 
                              type="number" 
                              required
                              min="0"
                              className="w-full px-2.5 py-1.5 border border-gray-200 rounded-lg text-sm font-bold"
                              value={v.dailyRate}
                              onChange={(e) => handleVehicleChange(index, 'dailyRate', e.target.value)}
                            />
                          </td>
                          <td className="px-4 py-2">
                            <input 
                              type="number" 
                              required
                              min="0"
                              className="w-full px-2.5 py-1.5 border border-gray-200 rounded-lg text-sm font-bold"
                              value={v.driverAllowance}
                              onChange={(e) => handleVehicleChange(index, 'driverAllowance', e.target.value)}
                            />
                          </td>
                          <td className="px-4 py-2">
                            <input 
                              type="number" 
                              required
                              min="0"
                              className="w-full px-2.5 py-1.5 border border-gray-200 rounded-lg text-sm font-bold"
                              value={v.tollCharges}
                              onChange={(e) => handleVehicleChange(index, 'tollCharges', e.target.value)}
                            />
                          </td>
                          <td className="px-4 py-2">
                            <input 
                              type="number" 
                              required
                              min="0"
                              className="w-full px-2.5 py-1.5 border border-gray-200 rounded-lg text-sm font-bold"
                              value={v.permitTax}
                              onChange={(e) => handleVehicleChange(index, 'permitTax', e.target.value)}
                            />
                          </td>
                          <td className="px-4 py-2 text-center">
                            <button 
                              type="button"
                              onClick={() => removeVehicleRow(index)}
                              className="text-red-500 hover:text-red-700 p-1.5 hover:bg-red-50 rounded-lg transition-all"
                            >
                              <Trash2 size={16} />
                            </button>
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
            </div>

            {/* Row 3.5: Custom Activity Add-ons */}
            <div className="border-t border-gray-100 pt-6">
              <h3 className="text-sm font-bold text-indigo-600 uppercase tracking-wider mb-4 flex items-center gap-2">
                <Sliders size={16} /> Custom Activity Add-ons
              </h3>

              {/* Sub-form to add/edit single Option */}
              <div className="bg-gray-50/50 p-4 rounded-xl border border-gray-150 space-y-4 shadow-sm mb-4">
                <h4 className="text-xs font-bold text-gray-700 flex items-center gap-1.5">
                  <Plus size={14} className="text-indigo-600" />
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
                      className="w-full px-3 py-2 bg-white border border-gray-200 rounded-lg text-sm outline-none font-semibold text-gray-700"
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
                        className="w-full pl-7 pr-3 py-2 bg-white border border-gray-200 rounded-lg text-sm outline-none font-bold"
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
                        className="w-full px-3 py-2 bg-white border border-gray-200 rounded-lg text-sm outline-none pr-8 font-semibold text-gray-700"
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
                      className="w-full px-3 py-2 bg-white border border-gray-200 rounded-lg text-sm outline-none font-semibold text-gray-700"
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
                      onClick={() => {
                        setOptionData({
                          categoryName: '',
                          optionName: '',
                          basePrice: '',
                          markupPercentage: 0,
                          price: 0
                        });
                        setEditingOptionIndex(null);
                      }}
                      className="px-3.5 py-1.5 bg-gray-150 hover:bg-gray-200 text-gray-700 text-xs font-bold rounded-lg transition-all"
                    >
                      Cancel
                    </button>
                  )}
                  <button
                    type="button"
                    onClick={handleAddCustomActivity}
                    className="px-4 py-1.5 bg-indigo-600 hover:bg-indigo-700 text-white text-xs font-bold rounded-lg transition-all flex items-center gap-1 shadow-md shadow-indigo-600/10 hover:shadow-lg"
                  >
                    {editingOptionIndex !== null ? 'Update Option' : 'Add Option'}
                  </button>
                </div>
              </div>

              {/* List of custom activities */}
              {(!formData.customActivities || formData.customActivities.length === 0) ? (
                <div className="p-6 bg-white rounded-xl border border-dashed border-gray-200 text-center flex flex-col items-center justify-center gap-2">
                  <AlertCircle className="h-8 w-8 text-gray-300 animate-pulse" />
                  <p className="text-xs font-bold text-gray-500">No custom activity add-ons configured yet</p>
                  <p className="text-[10px] text-gray-400">Add categories, options, and pricing above to build your configuration.</p>
                </div>
              ) : (
                <div className="space-y-2 max-h-80 overflow-y-auto pr-1">
                  {formData.customActivities.map((opt, idx) => {
                    const iconName = categories.find(c => c.name === opt.categoryName)?.icon || 'HelpCircle';
                    const IconComponent = ICON_MAP[iconName] || HelpCircle;
                    return (
                      <div 
                        key={idx} 
                        className="flex items-center justify-between p-3 bg-white border border-gray-200 rounded-xl hover:border-indigo-300 transition-all shadow-sm group"
                      >
                        <div className="flex items-center gap-3 min-w-0 flex-1">
                          <div className="p-2 bg-indigo-50 text-indigo-600 rounded-lg shadow-sm">
                            <IconComponent className="h-4 w-4" />
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

                        <div className="flex items-center gap-3 shrink-0">
                          <div className="text-right">
                            <span className="text-xs font-extrabold text-indigo-600 block">
                              ₹{formatCurrency(opt.price)}
                            </span>
                            <span className="text-[9px] font-semibold text-gray-400">
                              INR
                            </span>
                          </div>
                          
                          <div className="flex gap-1">
                            <button
                              type="button"
                              onClick={() => handleEditCustomActivity(idx)}
                              className="p-1.5 text-blue-500 hover:bg-blue-50 rounded border border-transparent hover:border-blue-100 transition-colors"
                              title="Edit Option"
                            >
                              <Edit2 size={13} />
                            </button>
                            <button
                              type="button"
                              onClick={() => handleDeleteCustomActivity(idx)}
                              className="p-1.5 text-red-400 hover:bg-red-50 rounded border border-transparent hover:border-red-100 transition-colors"
                              title="Remove Option"
                            >
                              <Trash2 size={13} />
                            </button>
                          </div>
                        </div>
                      </div>
                    );
                  })}

                  {/* Total Cost Summary */}
                  <div className="p-3.5 bg-indigo-50/50 border border-indigo-100 rounded-xl flex items-center justify-between mt-3 shadow-inner">
                    <div>
                      <p className="text-[10px] font-extrabold text-indigo-600 uppercase tracking-widest">Total Configuration Pricing</p>
                      <p className="text-xs text-gray-500 mt-0.5">Sum of all individual items</p>
                    </div>
                    <div className="text-right">
                      <span className="text-lg font-black text-indigo-800 flex items-center gap-0.5 justify-end">
                        ₹{formatCurrency(formData.customActivities.reduce((sum, opt) => sum + opt.price, 0))}
                      </span>
                      <span className="text-[9px] font-bold text-indigo-600 uppercase tracking-wider">INR</span>
                    </div>
                  </div>
                </div>
              )}
            </div>
            
            {/* Row 4: Map Packages by ID */}
            <div className="border-t border-gray-100 pt-6">
              <div className="flex flex-col sm:flex-row justify-between sm:items-center gap-3 mb-4">
                <div>
                  <h3 className="text-sm font-bold text-indigo-600 uppercase tracking-wider flex items-center gap-2">
                    <Package size={16} /> Map to Packages ({formData.mappedPackageIds.length} Mapped)
                  </h3>
                  <p className="text-xs text-gray-400 mt-0.5">Select package IDs that should inherit this hotel and vehicle rate configuration sheet.</p>
                </div>
                
                {/* Package Search bar */}
                <div className="relative w-full sm:w-64">
                  <Search size={16} className="absolute left-3 top-1/2 -translate-y-1/2 text-gray-400" />
                  <input 
                    type="text" 
                    placeholder="Search package code/title..."
                    className="w-full pl-9 pr-4 py-1.5 text-xs bg-gray-50 border border-gray-200 rounded-lg"
                    value={packageSearch}
                    onChange={(e) => setPackageSearch(e.target.value)}
                  />
                </div>
              </div>

              {/* Package checkbox grids */}
              {filteredPackages.length === 0 ? (
                <div className="text-center py-8 border border-dashed border-gray-200 rounded-xl">
                  <p className="text-xs text-gray-400 font-bold">No packages found match search query.</p>
                </div>
              ) : (
                <div className="space-y-3">
                  <div className="flex gap-2">
                    <button 
                      type="button" 
                      onClick={() => selectAllFilteredPackages(filteredPackages)}
                      className="text-[10px] bg-gray-100 hover:bg-gray-200 font-bold text-gray-600 px-2.5 py-1 rounded"
                    >
                      Select All Filtered
                    </button>
                    <button 
                      type="button" 
                      onClick={() => deselectAllFilteredPackages(filteredPackages)}
                      className="text-[10px] bg-gray-100 hover:bg-gray-200 font-bold text-gray-600 px-2.5 py-1 rounded"
                    >
                      Clear All Filtered
                    </button>
                  </div>
                  <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-3 max-h-60 overflow-y-auto p-1.5 border border-gray-150 rounded-xl">
                    {filteredPackages.map(pkg => {
                      const isSelected = formData.mappedPackageIds.includes(pkg.id);
                      return (
                        <div 
                          key={pkg.id}
                          onClick={() => togglePackageSelection(pkg.id)}
                          className={`flex items-start gap-2.5 p-3 rounded-xl border cursor-pointer select-none transition-all ${
                            isSelected 
                              ? 'border-indigo-500 bg-indigo-50/30' 
                              : 'border-gray-200 bg-white hover:border-gray-300'
                          }`}
                        >
                          <div className="text-indigo-600 shrink-0 mt-0.5">
                            {isSelected ? <CheckSquare size={16} /> : <Square size={16} className="text-gray-300" />}
                          </div>
                          <div className="min-w-0">
                            <span className="text-[10px] font-black text-indigo-700 bg-indigo-50 px-2 py-0.5 rounded mr-2 uppercase">
                              {pkg.packageCode || 'CODE'}
                            </span>
                            <span className="text-xs font-bold text-gray-700 truncate block mt-1">
                              {pkg.title}
                            </span>
                            <span className="text-[10px] font-semibold text-gray-400 block truncate">
                              {pkg.destination} ({pkg.duration?.days}D/{pkg.duration?.nights}N)
                            </span>
                          </div>
                        </div>
                      );
                    })}
                  </div>
                </div>
              )}
            </div>
          </div>

          <div className="p-6 bg-gray-50 border-t border-gray-100 flex gap-3 justify-end">
            <button 
              type="button"
              onClick={() => setShowForm(false)}
              className="px-6 py-2.5 border border-gray-200 text-gray-600 font-bold text-sm rounded-xl hover:bg-white transition-all shadow-sm"
            >
              Cancel
            </button>
            <button 
              type="submit"
              disabled={saving}
              className="bg-indigo-600 hover:bg-indigo-700 text-white font-bold py-2.5 px-6 text-sm rounded-xl shadow-md hover:shadow-lg transition-all flex items-center gap-2 disabled:opacity-50"
            >
              {saving ? (
                <>
                  <div className="w-4 h-4 border-2 border-white border-t-transparent rounded-full animate-spin" />
                  Saving...
                </>
              ) : (
                <>
                  <Save size={16} />
                  {editingId ? 'Update Rule' : 'Save Rule'}
                </>
              )}
            </button>
          </div>
        </form>
      ) : (
        /* Rules Listing View */
        <div className="space-y-4">
          {/* Filters Panel */}
          <div className="relative max-w-md">
            <Search size={18} className="absolute left-3.5 top-1/2 -translate-y-1/2 text-gray-400" />
            <input 
              type="text" 
              placeholder="Search allocation rules by code, title, description..."
              className="w-full pl-10 pr-4 py-2.5 bg-white border border-gray-100 rounded-2xl shadow-sm focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500 text-sm font-semibold transition-all"
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
            />
          </div>

          {filteredRules.length === 0 ? (
            <div className="bg-white text-center py-16 rounded-2xl border border-gray-100 shadow-sm">
              <Info size={40} className="mx-auto text-indigo-400 mb-3" />
              <h3 className="text-base font-bold text-gray-900 mb-1">No Allocation Rules Found</h3>
              <p className="text-xs text-gray-400 font-semibold mb-4">Get started by creating a new hotel and vehicle allocation pricing rule sheet.</p>
              <button 
                onClick={handleCreateNew}
                className="bg-indigo-600 hover:bg-indigo-700 text-white font-bold py-2 px-4 rounded-xl text-xs shadow-md transition-all"
              >
                Create First Rule
              </button>
            </div>
          ) : (
            <div className="grid grid-cols-1 xl:grid-cols-2 gap-4">
              {filteredRules.map(rule => (
                <div key={rule.id} className="bg-white rounded-2xl border border-gray-100 shadow-sm hover:shadow-md hover:border-indigo-200 transition-all p-5 flex flex-col justify-between gap-4">
                  <div>
                    {/* Header */}
                    <div className="flex justify-between items-start gap-2">
                      <div className="min-w-0">
                        <div className="flex items-center gap-2 mb-1.5 flex-wrap">
                          <span className="text-[10px] font-black text-indigo-700 bg-indigo-50 px-2 py-0.5 rounded uppercase">
                            {rule.ruleCode}
                          </span>
                          <span className={`text-[10px] font-black px-2 py-0.5 rounded ${
                            rule.status === 'ACTIVE' ? 'bg-green-50 text-green-700' : 'bg-red-50 text-red-700'
                          }`}>
                            {rule.status}
                          </span>
                          <span className="text-[10px] font-black bg-purple-50 text-purple-700 px-2 py-0.5 rounded uppercase">
                            {rule.packageType || 'FIT'}
                          </span>
                          <span className="text-[10px] font-black bg-amber-50 text-amber-700 px-2 py-0.5 rounded uppercase">
                            {rule.mealPlan || 'CP'}
                          </span>
                          {rule.includeSightseeing !== false && (
                            <span className="text-[10px] font-black bg-teal-50 text-teal-700 px-2 py-0.5 rounded uppercase">
                              Sightseeing Inc.
                            </span>
                          )}
                          <span className="text-[10px] font-semibold text-gray-400">
                            {rule.mappedPackageIds?.length || 0} packages mapped
                          </span>
                        </div>
                        <h3 className="font-extrabold text-gray-900 text-base leading-tight break-words truncate">
                          {rule.title}
                        </h3>
                        <p className="text-xs text-gray-400 font-semibold mt-1 line-clamp-1">
                          {rule.description || 'No description provided.'}
                        </p>
                      </div>

                      {/* Actions */}
                      <div className="flex gap-1.5">
                        <button 
                          onClick={() => handleEdit(rule)}
                          className="p-2 hover:bg-blue-50 text-blue-600 rounded-lg transition-all"
                          title="Edit Rule"
                        >
                          <Edit2 size={15} />
                        </button>
                        <button 
                          onClick={() => handleDelete(rule.id)}
                          className="p-2 hover:bg-red-50 text-red-500 rounded-lg transition-all"
                          title="Delete Rule"
                        >
                          <Trash2 size={15} />
                        </button>
                      </div>
                    </div>

                    {/* Rates Summary */}
                    <div className="grid grid-cols-2 sm:grid-cols-4 gap-2 bg-gray-50 p-3 rounded-xl mt-4 border border-gray-100">
                      <div>
                        <span className="text-[9px] text-gray-400 font-bold block uppercase tracking-wider">Base Room</span>
                        <span className="text-xs font-black text-gray-800">₹{formatCurrency(rule.baseRoomRate)}</span>
                      </div>
                      <div>
                        <span className="text-[9px] text-gray-400 font-bold block uppercase tracking-wider">Extra Adult</span>
                        <span className="text-xs font-black text-gray-800">₹{formatCurrency(rule.extraAdultRate)}</span>
                      </div>
                      <div>
                        <span className="text-[9px] text-gray-400 font-bold block uppercase tracking-wider">CWB / CNB</span>
                        <span className="text-xs font-black text-gray-800">₹{rule.cwbRate}/₹{rule.cnbRate}</span>
                      </div>
                      <div>
                        <span className="text-[9px] text-gray-400 font-bold block uppercase tracking-wider">Sightseeing</span>
                        <span className="text-xs font-black text-gray-800">₹{formatCurrency(rule.sightseeingTicketPrice)}</span>
                      </div>
                    </div>

                    {/* Vehicles summary */}
                    <div className="mt-4 space-y-1">
                      <span className="text-[10px] font-bold text-gray-400 uppercase tracking-wider">Configured Vehicles:</span>
                      <div className="flex flex-wrap gap-1.5 pt-1">
                        {(rule.vehicles || []).map((v, i) => (
                          <span 
                            key={i} 
                            className="bg-gray-100 border border-gray-150 text-gray-600 px-2 py-0.5 rounded text-[10px] font-semibold flex items-center gap-1.5 shadow-sm"
                          >
                            <Car size={10} className="text-indigo-500 shrink-0" />
                            {v.vehicleName} (Max {v.maxPax}): ₹{formatCurrency(v.dailyRate)}/d
                          </span>
                        ))}
                        {(!rule.vehicles || rule.vehicles.length === 0) && (
                          <span className="text-xs text-gray-400 italic font-semibold">None configured</span>
                        )}
                      </div>
                    </div>

                    {/* Custom Activities summary */}
                    {rule.customActivities && rule.customActivities.length > 0 && (
                      <div className="mt-3 space-y-1">
                        <span className="text-[10px] font-bold text-gray-400 uppercase tracking-wider">Custom Activity Add-ons:</span>
                        <div className="flex flex-wrap gap-1.5 pt-1">
                          {rule.customActivities.map((act, i) => (
                            <span 
                              key={i} 
                              className="bg-indigo-50/50 border border-indigo-100 text-indigo-700 px-2 py-0.5 rounded text-[10px] font-semibold flex items-center gap-1.5 shadow-sm"
                            >
                              <Sliders size={10} className="text-indigo-500 shrink-0" />
                              {act.optionName} ({act.categoryName}): ₹{formatCurrency(act.price)}
                            </span>
                          ))}
                        </div>
                      </div>
                    )}
                  </div>

                  {/* Audit Trail */}
                  {rule.audit && (
                    <div className="text-[9px] text-gray-400 font-semibold border-t border-gray-50 pt-2 flex justify-between flex-wrap gap-1 mt-1">
                      <span>Created by {rule.audit.createdBy || 'System'}</span>
                      <span>Last updated: {rule.audit.updatedAt ? new Date(rule.audit.updatedAt).toLocaleString() : 'N/A'}</span>
                    </div>
                  )}
                </div>
              ))}
            </div>
          )}
        </div>
      )}
    </div>
  );
}
