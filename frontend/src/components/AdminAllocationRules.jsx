import React, { useState, useEffect } from 'react';
import { 
  Plus, Edit2, Trash2, X, Search, ChevronDown, ChevronUp, 
  AlertCircle, IndianRupee, Save, Settings, Info, Package,
  Sliders, Calendar, User, Users, CheckSquare, Square
} from 'lucide-react';
import toast from 'react-hot-toast';
import api from '../services/api';
import { formatCurrency } from '../utils/formatUtils';

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
  vehicles: [
    { vehicleName: 'Sedan', maxPax: 4, dailyRate: 2000, tollCharges: 500, permitTax: 0, driverAllowance: 300 },
    { vehicleName: 'SUV', maxPax: 6, dailyRate: 2500, tollCharges: 600, permitTax: 0, driverAllowance: 300 },
    { vehicleName: 'Innova Crysta', maxPax: 6, dailyRate: 3000, tollCharges: 800, permitTax: 0, driverAllowance: 300 },
    { vehicleName: 'Tempo Traveller', maxPax: 12, dailyRate: 5000, tollCharges: 1500, permitTax: 500, driverAllowance: 500 }
  ],
  mappedPackageIds: []
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
  const [loading, setLoading] = useState(true);
  const [formData, setFormData] = useState(defaultRuleForm);
  const [editingId, setEditingId] = useState(null);
  const [saving, setSaving] = useState(false);
  const [searchQuery, setSearchQuery] = useState('');
  const [showForm, setShowForm] = useState(false);
  const [packageSearch, setPackageSearch] = useState('');

  useEffect(() => {
    fetchData();
  }, []);

  const fetchData = async () => {
    try {
      setLoading(true);
      const [rulesRes, packagesRes] = await Promise.all([
        api.get('/allocation-rules/all'),
        api.get('/packages/all')
      ]);
      setRules(Array.isArray(rulesRes.data) ? rulesRes.data : []);
      setPackages(Array.isArray(packagesRes.data) ? packagesRes.data : []);
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
      mappedPackageIds: []
    });
    setEditingId(null);
    setShowForm(true);
  };

  const handleEdit = (rule) => {
    setFormData({
      ...defaultRuleForm,
      ...rule,
      vehicles: Array.isArray(rule.vehicles) ? rule.vehicles : [],
      mappedPackageIds: Array.isArray(rule.mappedPackageIds) ? rule.mappedPackageIds : []
    });
    setEditingId(rule.id);
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
    r.ruleCode?.toLowerCase().includes(searchQuery.toLowerCase()) ||
    r.title?.toLowerCase().includes(searchQuery.toLowerCase()) ||
    r.description?.toLowerCase().includes(searchQuery.toLowerCase())
  );

  const filteredPackages = packages.filter(p => 
    p.title?.toLowerCase().includes(packageSearch.toLowerCase()) ||
    p.packageCode?.toLowerCase().includes(packageSearch.toLowerCase()) ||
    p.destination?.toLowerCase().includes(packageSearch.toLowerCase())
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
