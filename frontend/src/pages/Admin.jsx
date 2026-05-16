import React, { useState, useEffect } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { LogOut, Plus, Edit2, Trash2, X, Upload, Image, Settings, FileText, Copy, Search, ChevronDown, ChevronUp, GripVertical } from 'lucide-react';
import toast from 'react-hot-toast';
import ReactQuill from 'react-quill-new';
import 'react-quill-new/dist/quill.snow.css';
import api from '../services/api';
import { formatCurrency } from '../utils/formatUtils';

const quillModules = {
  toolbar: [
    [{ 'header': [1, 2, false] }],
    ['bold', 'italic', 'underline', 'strike', 'blockquote'],
    [{'list': 'ordered'}, {'list': 'bullet'}, {'indent': '-1'}, {'indent': '+1'}],
    ['link', 'clean']
  ],
};

const quillFormats = [
  'header',
  'bold', 'italic', 'underline', 'strike', 'blockquote',
  'list', 'bullet', 'indent',
  'link'
];

const defaultForm = {
  packageCode: '',
  title: '',
  destination: '',
  status: 'ACTIVE',
  packageType: 'Domestic',
  overview: '',
  duration: { days: '', nights: '' },
  pricing: { currency: 'INR', basePrice: '', discountPercentage: '', finalPrice: '' },
  media: { thumbnailUrl: '', galleryUrls: [], altText: '' },
  inclusions: [],
  exclusions: [],
  itinerary: [],
  specialNotes: '',
  version: 0
};

const Admin = () => {
  const [packages, setPackages] = useState([]);
  const [formData, setFormData] = useState(defaultForm);
  const [editingId, setEditingId] = useState(null);
  const [uploading, setUploading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [mediaContexts, setMediaContexts] = useState({ thumbnail: '', gallery: {} });
  const [view, setView] = useState('packages');
  const [globalTerms, setGlobalTerms] = useState('');
  const [savingSettings, setSavingSettings] = useState(false);
  const navigate = useNavigate();

  // Clone-from-package state (standard)
  const [clonePanelOpen, setClonePanelOpen] = useState(false);
  const [cloneCode, setCloneCode] = useState('');
  const [cloneFetching, setCloneFetching] = useState(false);
  const [cloneStatus, setCloneStatus] = useState(null);

  // Custom package state
  const [isCustomPackage, setIsCustomPackage] = useState(false);
  const [customFetchCode, setCustomFetchCode] = useState('');
  const [customFetching, setCustomFetching] = useState(false);
  const [customFetchStatus, setCustomFetchStatus] = useState(null);
  const [customFetchPanelOpen, setCustomFetchPanelOpen] = useState(false);

  // Send PDF to client state
  const defaultClientInfo = { firstName: '', lastName: '', email: '', phone: '', location: '' };
  const [clientInfo, setClientInfo] = useState(defaultClientInfo);
  const [sendPdfPanelOpen, setSendPdfPanelOpen] = useState(false);
  const [sendingPdf, setSendingPdf] = useState(false);

  // Package list search
  const [packageSearch, setPackageSearch] = useState('');

  // Itinerary drag-and-drop
  const dragIndexRef = React.useRef(null);
  const [dragOverIndex, setDragOverIndex] = useState(null);

  const getTokenPayload = () => {
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
  const userProfile = getTokenPayload();

  const getNextPackageCode = (currentPackages) => {
    if (!currentPackages || currentPackages.length === 0) return 'PKG-001';
    let maxNumber = 0;
    currentPackages.forEach(pkg => {
      const match = pkg.packageCode?.match(/^PKG-(\d+)$/i);
      if (match) {
        const num = parseInt(match[1], 10);
        if (num > maxNumber) maxNumber = num;
      }
    });
    return `PKG-${String(maxNumber + 1).padStart(3, '0')}`;
  };

  const getNextCustomPackageCode = (currentPackages) => {
    if (!currentPackages || currentPackages.length === 0) return 'CUSPKG-001';
    let maxNumber = 0;
    currentPackages.forEach(pkg => {
      const match = pkg.packageCode?.match(/^CUSPKG-(\d+)$/i);
      if (match) {
        const num = parseInt(match[1], 10);
        if (num > maxNumber) maxNumber = num;
      }
    });
    return `CUSPKG-${String(maxNumber + 1).padStart(3, '0')}`;
  };

  const handleToggleCustomPackage = (enable) => {
    setIsCustomPackage(enable);
    setCustomFetchCode('');
    setCustomFetchStatus(null);
    setCustomFetchPanelOpen(false);
    setSendPdfPanelOpen(false);
    setClientInfo(defaultClientInfo);
    if (!editingId) {
      const newCode = enable
        ? getNextCustomPackageCode(packages)
        : getNextPackageCode(packages);
      setFormData(prev => ({ ...prev, packageCode: newCode }));
    }
  };

  const handleSendPdf = async () => {
    // Validate client info
    if (!clientInfo.firstName.trim()) { toast.error('First name is required.'); return; }
    if (!clientInfo.email.trim() || !/^[A-Za-z0-9+_.-]+@(.+)$/.test(clientInfo.email)) {
      toast.error('A valid email address is required.'); return;
    }
    if (!clientInfo.phone.trim() || clientInfo.phone.trim().length < 10) {
      toast.error('A valid phone number (min 10 digits) is required.'); return;
    }
    if (!clientInfo.location.trim()) { toast.error('Location is required.'); return; }

    // The package must be saved first (we need its id)
    if (!formData.id && !editingId) {
      toast.error('Please save the custom package first before sending the PDF.');
      return;
    }
    const pkgId = editingId || formData.id;

    setSendingPdf(true);
    try {
      await api.post('/bookings', {
        firstName: clientInfo.firstName.trim(),
        lastName: clientInfo.lastName.trim(),
        email: clientInfo.email.trim(),
        phone: clientInfo.phone.trim(),
        location: clientInfo.location.trim(),
        packageId: pkgId,
        packageTitle: formData.title,
      });
      toast.success(`📧 Package PDF sent to ${clientInfo.email}!`);
      setClientInfo(defaultClientInfo);
      setSendPdfPanelOpen(false);
    } catch (err) {
      console.error('Send PDF error:', err);
      toast.error(err.response?.data?.error || 'Failed to send PDF. Please try again.');
    } finally {
      setSendingPdf(false);
    }
  };

  const handleCustomFetch = async () => {
    const code = customFetchCode.trim().toUpperCase();
    if (!code) { toast.error('Please enter a package code.'); return; }
    setCustomFetching(true);
    setCustomFetchStatus(null);
    try {
      let source = packages.find(p => p.packageCode?.toUpperCase() === code);
      if (!source) {
        const freshList = await fetchPackages();
        source = freshList.find(p => p.packageCode?.toUpperCase() === code);
      }
      if (!source) {
        setCustomFetchStatus('error');
        toast.error(`No package found with code "${code}"`);
        return;
      }
      const latestPackages = packages.length > 0 ? packages : await fetchPackages();
      const newCode = getNextCustomPackageCode(latestPackages);
      setFormData({
        ...defaultForm,
        ...source,
        packageCode: newCode,
        status: 'ACTIVE',
        duration: source.duration || defaultForm.duration,
        pricing: source.pricing || defaultForm.pricing,
        media: source.media || defaultForm.media,
        inclusions: source.inclusions ? [...source.inclusions] : [],
        exclusions: source.exclusions ? [...source.exclusions] : [],
        itinerary: source.itinerary ? source.itinerary.map(d => ({ ...d })) : [],
        specialNotes: source.specialNotes || '',
        version: 0,
        id: undefined,
      });
      setEditingId(null);
      setMediaContexts({ thumbnail: '', gallery: {} });
      setCustomFetchStatus('success');
      toast.success(`Details fetched from "${source.title}" — assigned code ${newCode}`);
    } catch (err) {
      console.error('Custom fetch error:', err);
      setCustomFetchStatus('error');
      toast.error('Failed to fetch source package.');
    } finally {
      setCustomFetching(false);
    }
  };

  useEffect(() => {
    if (!userProfile) {
      handleLogout();
      return;
    }
    
    if (userProfile.role !== 'ADMIN') {
      toast.error('You do not have permission to access the admin dashboard');
      navigate('/');
      return;
    }
    
    fetchPackages();
    fetchGlobalTerms();
  }, []);

  const fetchGlobalTerms = async () => {
    try {
      const response = await api.get('/settings/terms');
      setGlobalTerms(response.data.termsAndConditions || '');
    } catch (error) {
      console.error('Error fetching global terms:', error);
    }
  };

  const handleSaveGlobalTerms = async () => {
    setSavingSettings(true);
    try {
      await api.put('/settings/terms', { termsAndConditions: globalTerms });
      toast.success('Global Terms & Conditions updated successfully');
    } catch (error) {
      console.error('Error saving global terms:', error);
      toast.error('Failed to save global terms');
    } finally {
      setSavingSettings(false);
    }
  };

  const fetchPackages = async () => {
    try {
      const response = await api.get('/packages/all');
      setPackages(response.data);
      setFormData(prev => ({ 
        ...prev, 
        packageCode: editingId ? prev.packageCode : getNextPackageCode(response.data) 
      }));
      return response.data;
    } catch (error) {
      if (error.response && error.response.status === 403) {
        handleLogout();
      }
      return [];
    }
  };

  const handleCloneFetch = async () => {
    const code = cloneCode.trim().toUpperCase();
    if (!code) {
      toast.error('Please enter a package code to clone from.');
      return;
    }
    setCloneFetching(true);
    setCloneStatus(null);
    try {
      // First try from already-loaded packages list
      let source = packages.find(p => p.packageCode?.toUpperCase() === code);

      // Fallback: fetch fresh list from API
      if (!source) {
        const freshList = await fetchPackages();
        source = freshList.find(p => p.packageCode?.toUpperCase() === code);
      }

      if (!source) {
        setCloneStatus('error');
        toast.error(`No package found with code "${code}"`);
        return;
      }

      // Determine the next available package code for the clone
      const latestPackages = packages.length > 0 ? packages : await fetchPackages();
      const newCode = getNextPackageCode(latestPackages);

      // Populate the form with cloned data but a new package code
      setFormData({
        ...defaultForm,
        ...source,
        packageCode: newCode,          // new auto-generated code
        status: 'ACTIVE',              // always start active
        duration: source.duration || defaultForm.duration,
        pricing: source.pricing || defaultForm.pricing,
        media: source.media || defaultForm.media,
        inclusions: source.inclusions ? [...source.inclusions] : [],
        exclusions: source.exclusions ? [...source.exclusions] : [],
        itinerary: source.itinerary ? source.itinerary.map(d => ({ ...d })) : [],
        specialNotes: source.specialNotes || '',
        version: 0,
        id: undefined,
      });
      setEditingId(null); // treat as new package
      setMediaContexts({ thumbnail: '', gallery: {} });
      setCloneStatus('success');
      toast.success(`Package cloned from "${source.title}" — review and save as new.`);
    } catch (err) {
      console.error('Clone fetch error:', err);
      setCloneStatus('error');
      toast.error('Failed to fetch source package.');
    } finally {
      setCloneFetching(false);
    }
  };

  const handleLogout = () => {
    localStorage.removeItem('token');
    navigate('/login');
  };

  const handleTopLevelChange = (e) => {
    const { name, value } = e.target;
    setFormData({ ...formData, [name]: value });
  };

  const handleNestedChange = (category, field, value) => {
    let newFormData = {
      ...formData,
      [category]: { ...formData[category], [field]: value }
    };

    if (category === 'pricing' && (field === 'basePrice' || field === 'discountPercentage')) {
      const base = field === 'basePrice' ? Number(value) : Number(newFormData.pricing.basePrice);
      const discount = field === 'discountPercentage' ? Number(value) : Number(newFormData.pricing.discountPercentage);
      if (!isNaN(base) && !isNaN(discount)) {
        newFormData.pricing.finalPrice = base - (base * (discount / 100));
      }
    }
    setFormData(newFormData);
  };

  const handleArrayStringChange = (category, index, value) => {
    let newArray;
    if (category === 'galleryUrls') {
      newArray = [...formData.media.galleryUrls];
      newArray[index] = value;
      setFormData({ ...formData, media: { ...formData.media, galleryUrls: newArray } });
    } else {
      newArray = [...formData[category]];
      newArray[index] = value;
      setFormData({ ...formData, [category]: newArray });
    }
  };

  const addArrayStringItem = (category) => {
    if (category === 'galleryUrls') {
      setFormData({ ...formData, media: { ...formData.media, galleryUrls: [...formData.media.galleryUrls, ''] } });
    } else {
      setFormData({ ...formData, [category]: [...formData[category], ''] });
    }
  };

  const removeArrayStringItem = (category, index) => {
    if (category === 'galleryUrls') {
      const newArray = formData.media.galleryUrls.filter((_, i) => i !== index);
      setFormData({ ...formData, media: { ...formData.media, galleryUrls: newArray } });
    } else {
      const newArray = formData[category].filter((_, i) => i !== index);
      setFormData({ ...formData, [category]: newArray });
    }
  };

  const handleItineraryChange = (index, field, value) => {
    const newItinerary = [...formData.itinerary];
    newItinerary[index] = { ...newItinerary[index], [field]: value };
    setFormData({ ...formData, itinerary: newItinerary });
  };

  const addItineraryDay = () => {
    setFormData({
      ...formData,
      itinerary: [...formData.itinerary, { day: formData.itinerary.length + 1, title: '', activities: '' }]
    });
  };

  const removeItineraryDay = (index) => {
    const newItinerary = formData.itinerary.filter((_, i) => i !== index);
    const adjustedItinerary = newItinerary.map((item, i) => ({ ...item, day: i + 1 }));
    setFormData({ ...formData, itinerary: adjustedItinerary });
  };

  const handleQuillChange = (name, value) => {
    setFormData(prev => ({ ...prev, [name]: value }));
  };

  const handleItineraryQuillChange = (index, value) => {
    const newItinerary = [...formData.itinerary];
    newItinerary[index] = { ...newItinerary[index], activities: value };
    setFormData({ ...formData, itinerary: newItinerary });
  };

  const handleImageUpload = async (e, type, index = null, context = '') => {
    const file = e.target.files[0];
    if (!file) return;

    setUploading(true);
    const formDataPayload = new FormData();
    formDataPayload.append('file', file);
    if (context && context.trim() !== '') {
      formDataPayload.append('context', context);
    }

    try {
      const response = await api.post('/media/upload', formDataPayload, {
        headers: { 'Content-Type': 'multipart/form-data' }
      });
      const url = response.data.url;

      if (type === 'thumbnail') {
        handleNestedChange('media', 'thumbnailUrl', url);
        setMediaContexts(prev => ({ ...prev, thumbnail: '' })); // clear after upload
      } else if (type === 'gallery') {
        handleArrayStringChange('galleryUrls', index, url);
        setMediaContexts(prev => {
          const newGallery = { ...prev.gallery };
          delete newGallery[index];
          return { ...prev, gallery: newGallery };
        });
      }
    } catch (error) {
      console.error('Error uploading image', error);
      const errorMessage = error.response?.data?.error || error.message || 'Failed to upload image';
      toast.error(`Upload failed: ${errorMessage}`);
    } finally {
      setUploading(false);
    }
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (uploading) {
      toast.error("Please wait for image upload to finish.");
      return;
    }

    const payload = { ...formData };
    payload.duration = {
      days: formData.duration.days ? Number(formData.duration.days) : 0,
      nights: formData.duration.nights ? Number(formData.duration.nights) : 0
    };
    payload.pricing = {
      ...formData.pricing,
      basePrice: formData.pricing.basePrice ? Number(formData.pricing.basePrice) : 0,
      discountPercentage: formData.pricing.discountPercentage ? Number(formData.pricing.discountPercentage) : 0,
      finalPrice: formData.pricing.finalPrice ? Number(formData.pricing.finalPrice) : 0
    };

    setSaving(true);
    try {
      if (editingId) {
        await api.put(`/packages/${editingId}`, payload);
        toast.success('Package updated successfully');
      } else {
        await api.post('/packages', payload);
        toast.success('Package saved successfully');
      }
      setFormData(defaultForm);
      setEditingId(null);
      fetchPackages();
    } catch (error) {
      console.error('Error saving package', error);
      if (error.response && error.response.status === 409) {
        toast.error(error.response.data.message || 'This package code already exists.');
      } else if (error.response && error.response.status === 403) {
        handleLogout();
      } else {
        const errorMessage = error.response?.data?.error || error.response?.data?.message || error.message || 'Failed to save package';
        toast.error(`Failed to save package: ${errorMessage}`);
      }
    } finally {
      setSaving(false);
    }
  };

  const handleEdit = (pkg) => {
    setEditingId(pkg.id);
    setFormData({
      ...defaultForm,
      ...pkg,
      duration: pkg.duration || defaultForm.duration,
      pricing: pkg.pricing || defaultForm.pricing,
      media: pkg.media || defaultForm.media,
      inclusions: pkg.inclusions || [],
      exclusions: pkg.exclusions || [],
      itinerary: pkg.itinerary || [],
      specialNotes: pkg.specialNotes || '',
      version: pkg.version || 0
    });
  };

  const handleDelete = async (id) => {
    if (window.confirm('Are you sure you want to delete this package?')) {
      const previousPackages = [...packages];
      setPackages(packages.filter(p => p.id !== id));
      try {
        await api.delete(`/packages/${id}`);
        toast.success('Package deleted successfully');
      } catch (error) {
        setPackages(previousPackages);
        console.error('Error deleting package', error);
        if (error.response && error.response.status === 403) {
          handleLogout();
        } else {
          toast.error('Failed to delete package');
        }
      }
    }
  };

  return (
    <div className="min-h-screen bg-gray-100">
      <nav className="bg-white shadow-sm sticky top-0 z-10">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
          <div className="flex justify-between h-16 items-center">
            <div className="flex items-center space-x-4">
              <h1 className="text-xl font-bold text-gray-900">Admin Dashboard</h1>
              <Link to="/" className="text-blue-600 hover:text-blue-800 text-sm font-medium hidden sm:block">
                View Website
              </Link>
              <Link to="/admin/images" className="flex items-center text-blue-600 hover:text-blue-800 text-sm font-medium hidden sm:block">
                <Image className="h-4 w-4 mr-1" /> Media Gallery
              </Link>
              <button 
                onClick={() => setView(view === 'packages' ? 'settings' : 'packages')}
                className="flex items-center text-blue-600 hover:text-blue-800 text-sm font-medium"
              >
                <Settings className="h-4 w-4 mr-1" /> {view === 'packages' ? 'Global Terms' : 'Manage Packages'}
              </button>
            </div>
            <div className="flex items-center space-x-4">
              {userProfile && userProfile.picture && (
                <div className="flex items-center space-x-2">
                  <img src={userProfile.picture} alt={userProfile.name} className="w-8 h-8 rounded-full border border-gray-300 object-cover" />
                  <span className="text-sm font-medium text-gray-700 hidden md:block">{userProfile.name}</span>
                </div>
              )}
              <button onClick={handleLogout} className="flex items-center text-gray-600 hover:text-gray-900">
                <LogOut className="h-5 w-5 mr-1" /> Logout
              </button>
            </div>
          </div>
        </div>
      </nav>

      <main className="max-w-7xl mx-auto py-6 sm:px-6 lg:px-8">
        <div className="flex flex-col lg:flex-row gap-6">
          
          {/* Settings Section */}
          {view === 'settings' && (
            <div className="w-full bg-white p-8 rounded-lg shadow-sm border border-gray-200">
              <div className="flex items-center gap-3 mb-6 border-b pb-4">
                <FileText className="h-6 w-6 text-blue-600" />
                <h2 className="text-2xl font-bold text-gray-900">Global Terms & Conditions</h2>
              </div>
              <p className="text-gray-500 mb-6 italic">
                These terms will be displayed at the bottom of all package detail pages.
              </p>
              <div className="mb-8">
                <ReactQuill 
                  theme="snow"
                  value={globalTerms} 
                  onChange={setGlobalTerms}
                  modules={quillModules}
                  formats={quillFormats}
                  placeholder="Enter global terms and conditions..."
                  className="bg-white rounded-md h-96 mb-12"
                />
              </div>
              <div className="flex justify-end pt-8">
                <button 
                  onClick={handleSaveGlobalTerms}
                  disabled={savingSettings}
                  className="flex items-center px-8 py-3 border border-transparent rounded-md text-white bg-blue-600 hover:bg-blue-700 shadow-md font-bold text-lg disabled:bg-blue-400 transition-all"
                >
                  {savingSettings ? 'Saving...' : 'Save Global Terms'}
                </button>
              </div>
            </div>
          )}

          {view === 'packages' && (
            <>
              {/* Form Section */}
              <div className={`lg:w-1/2 bg-white p-6 rounded-lg shadow-sm border overflow-y-auto max-h-[calc(100vh-8rem)] ${
                isCustomPackage ? 'border-purple-300 ring-1 ring-purple-200' : 'border-gray-200'
              }`}>
                <div className="flex items-center justify-between mb-4 border-b pb-3">
                  <h2 className="text-xl font-bold text-gray-900">
                    {editingId ? 'Edit Package' : (isCustomPackage ? '✦ Custom Package' : 'Add New Package')}
                  </h2>
                  {!editingId && (
                    <div className="flex items-center gap-1 bg-gray-100 rounded-lg p-1">
                      <button
                        type="button"
                        onClick={() => handleToggleCustomPackage(false)}
                        className={`px-3 py-1 rounded-md text-xs font-semibold transition-all ${
                          !isCustomPackage ? 'bg-white text-blue-700 shadow' : 'text-gray-500 hover:text-gray-700'
                        }`}
                      >Standard</button>
                      <button
                        type="button"
                        onClick={() => handleToggleCustomPackage(true)}
                        className={`px-3 py-1 rounded-md text-xs font-semibold transition-all ${
                          isCustomPackage ? 'bg-purple-600 text-white shadow' : 'text-gray-500 hover:text-gray-700'
                        }`}
                      >✦ Custom</button>
                    </div>
                  )}
                </div>
                {isCustomPackage && !editingId && (
                  <div className="mb-4 rounded-lg border border-dashed border-purple-300 bg-purple-50 text-purple-800 px-4 py-2 text-xs">
                    This package will receive a unique <span className="font-bold font-mono">CUSPKG-XXX</span> code and will be listed alongside standard packages.
                  </div>
                )}

                {/* ── Clone / Fetch panel (Standard mode) ── */}
                {!editingId && !isCustomPackage && (
                  <div className="mb-6 rounded-lg border border-dashed border-blue-300 bg-blue-50 overflow-hidden">
                    <button
                      type="button"
                      onClick={() => { setClonePanelOpen(o => !o); setCloneStatus(null); }}
                      className="w-full flex items-center justify-between px-4 py-3 text-blue-700 hover:bg-blue-100 transition-colors"
                    >
                      <span className="flex items-center gap-2 font-semibold text-sm">
                        <Copy size={16} />
                        Clone from an Existing Package
                      </span>
                      {clonePanelOpen ? <ChevronUp size={16} /> : <ChevronDown size={16} />}
                    </button>
                    {clonePanelOpen && (
                      <div className="px-4 pb-4 pt-2 border-t border-blue-200">
                        <p className="text-xs text-blue-600 mb-3">Enter the source package code. All fields will be pre-filled; save as a new package.</p>
                        <div className="flex gap-2">
                          <input
                            id="clone-code-input"
                            type="text"
                            value={cloneCode}
                            onChange={e => { setCloneCode(e.target.value.toUpperCase()); setCloneStatus(null); }}
                            onKeyDown={e => e.key === 'Enter' && (e.preventDefault(), handleCloneFetch())}
                            placeholder="e.g. PKG-004"
                            className={`flex-1 rounded border p-2 text-sm font-mono uppercase tracking-wider bg-white focus:outline-none focus:ring-2 ${
                              cloneStatus === 'success' ? 'border-green-400 focus:ring-green-300' :
                              cloneStatus === 'error'   ? 'border-red-400 focus:ring-red-300' :
                              'border-blue-300 focus:ring-blue-300'
                            }`}
                          />
                          <button
                            id="clone-fetch-btn"
                            type="button"
                            onClick={handleCloneFetch}
                            disabled={cloneFetching || !cloneCode.trim()}
                            className="flex items-center gap-2 px-4 py-2 rounded bg-blue-600 text-white text-sm font-semibold hover:bg-blue-700 disabled:bg-blue-300 transition-colors whitespace-nowrap"
                          >
                            {cloneFetching ? <span className="animate-spin inline-block w-4 h-4 border-2 border-white border-t-transparent rounded-full" /> : <Search size={15} />}
                            {cloneFetching ? 'Fetching…' : 'Fetch & Clone'}
                          </button>
                        </div>
                        {cloneStatus === 'success' && <p className="mt-2 text-xs text-green-700 font-medium">✅ Fields populated! Review and save below.</p>}
                        {cloneStatus === 'error' && <p className="mt-2 text-xs text-red-600 font-medium">❌ Package not found. Check the code and try again.</p>}
                      </div>
                    )}
                  </div>
                )}

                {/* ── Fetch from existing (Custom mode) ── */}
                {!editingId && isCustomPackage && (
                  <div className="mb-6 rounded-lg border border-dashed border-purple-300 bg-purple-50 overflow-hidden">
                    <button
                      type="button"
                      onClick={() => { setCustomFetchPanelOpen(o => !o); setCustomFetchStatus(null); }}
                      className="w-full flex items-center justify-between px-4 py-3 text-purple-700 hover:bg-purple-100 transition-colors"
                    >
                      <span className="flex items-center gap-2 font-semibold text-sm">
                        <Copy size={16} />
                        Fetch Details from an Existing Package
                      </span>
                      {customFetchPanelOpen ? <ChevronUp size={16} /> : <ChevronDown size={16} />}
                    </button>
                    {customFetchPanelOpen && (
                      <div className="px-4 pb-4 pt-2 border-t border-purple-200">
                        <p className="text-xs text-purple-600 mb-3">Enter any existing package code (e.g. <span className="font-mono font-bold">PKG-002</span>). Fields will be pre-filled and a new <span className="font-mono font-bold">CUSPKG-XXX</span> code will be assigned.</p>
                        <div className="flex gap-2">
                          <input
                            id="custom-fetch-code-input"
                            type="text"
                            value={customFetchCode}
                            onChange={e => { setCustomFetchCode(e.target.value.toUpperCase()); setCustomFetchStatus(null); }}
                            onKeyDown={e => e.key === 'Enter' && (e.preventDefault(), handleCustomFetch())}
                            placeholder="e.g. PKG-002 or CUSPKG-001"
                            className={`flex-1 rounded border p-2 text-sm font-mono uppercase tracking-wider bg-white focus:outline-none focus:ring-2 ${
                              customFetchStatus === 'success' ? 'border-green-400 focus:ring-green-300' :
                              customFetchStatus === 'error'   ? 'border-red-400 focus:ring-red-300' :
                              'border-purple-300 focus:ring-purple-300'
                            }`}
                          />
                          <button
                            id="custom-fetch-btn"
                            type="button"
                            onClick={handleCustomFetch}
                            disabled={customFetching || !customFetchCode.trim()}
                            className="flex items-center gap-2 px-4 py-2 rounded bg-purple-600 text-white text-sm font-semibold hover:bg-purple-700 disabled:bg-purple-300 transition-colors whitespace-nowrap"
                          >
                            {customFetching ? <span className="animate-spin inline-block w-4 h-4 border-2 border-white border-t-transparent rounded-full" /> : <Search size={15} />}
                            {customFetching ? 'Fetching…' : 'Fetch Details'}
                          </button>
                        </div>
                        {customFetchStatus === 'success' && <p className="mt-2 text-xs text-green-700 font-medium">✅ Details loaded! A new CUSPKG code has been assigned. Review and save below.</p>}
                        {customFetchStatus === 'error' && <p className="mt-2 text-xs text-red-600 font-medium">❌ Package not found. Please check the code and try again.</p>}
                      </div>
                    )}
                  </div>
                )}

                <form onSubmit={handleSubmit} className="space-y-6">
                  
                  {/* Basic Info */}
                  <div className={`space-y-4 p-4 rounded-md ${ isCustomPackage ? 'bg-purple-50' : 'bg-gray-50' }`}>
                    <h3 className={`font-semibold ${ isCustomPackage ? 'text-purple-700' : 'text-gray-700' }`}>Basic Info</h3>
                    <div className="grid grid-cols-2 gap-4">
                      <div>
                        <label className="block text-sm font-medium text-gray-700">Package Code (Auto)</label>
                        <input readOnly type="text" name="packageCode" value={formData.packageCode} onChange={handleTopLevelChange} className={`mt-1 block w-full rounded-md shadow-sm p-2 border cursor-not-allowed font-mono ${ isCustomPackage ? 'bg-purple-100 text-purple-700 border-purple-300' : 'bg-gray-200 text-gray-600' }`} placeholder="e.g. PKG-001" />
                      </div>
                      <div>
                        <label className="block text-sm font-medium text-gray-700">Status</label>
                        <select name="status" value={formData.status} onChange={handleTopLevelChange} className="mt-1 block w-full rounded-md border-gray-300 shadow-sm focus:border-blue-500 focus:ring-blue-500 p-2 border bg-white">
                          <option value="ACTIVE">ACTIVE</option>
                          <option value="INACTIVE">INACTIVE</option>
                        </select>
                      </div>
                      <div className="col-span-2">
                        <label className="block text-sm font-medium text-gray-700">Package Type</label>
                        <select name="packageType" value={formData.packageType} onChange={handleTopLevelChange} className="mt-1 block w-full rounded-md border-blue-200 shadow-sm focus:border-blue-500 focus:ring-blue-500 p-2 border bg-white font-semibold text-blue-700">
                          <option value="Domestic">Domestic</option>
                          <option value="International">International</option>
                        </select>
                      </div>
                    </div>
                    <div>
                      <label className="block text-sm font-medium text-gray-700">Title</label>
                      <input type="text" name="title" value={formData.title} onChange={handleTopLevelChange} className="mt-1 block w-full rounded-md border-gray-300 shadow-sm focus:border-blue-500 focus:ring-blue-500 p-2 border bg-white" />
                    </div>
                    <div>
                      <label className="block text-sm font-medium text-gray-700">Destination</label>
                      <input type="text" name="destination" value={formData.destination} onChange={handleTopLevelChange} className="mt-1 block w-full rounded-md border-gray-300 shadow-sm focus:border-blue-500 focus:ring-blue-500 p-2 border bg-white" />
                    </div>
                    <div>
                      <label className="block text-sm font-medium text-gray-700 mb-1">Overview</label>
                      <ReactQuill 
                        theme="snow"
                        value={formData.overview} 
                        onChange={(value) => handleQuillChange('overview', value)}
                        modules={quillModules}
                        formats={quillFormats}
                        className="bg-white rounded-md overflow-hidden"
                      />
                    </div>
                  </div>

                  {/* Duration & Pricing */}
                  <div className="grid grid-cols-2 gap-4">
                    <div className="space-y-4 bg-gray-50 p-4 rounded-md">
                      <h3 className="font-semibold text-gray-700">Duration</h3>
                      <div>
                        <label className="block text-sm text-gray-600">Days</label>
                        <input type="number" value={formData.duration.days} onChange={(e) => handleNestedChange('duration', 'days', e.target.value)} className="w-full rounded border p-2 bg-white" />
                      </div>
                      <div>
                        <label className="block text-sm text-gray-600">Nights</label>
                        <input type="number" value={formData.duration.nights} onChange={(e) => handleNestedChange('duration', 'nights', e.target.value)} className="w-full rounded border p-2 bg-white" />
                      </div>
                    </div>

                    <div className="space-y-4 bg-gray-50 p-4 rounded-md">
                      <h3 className="font-semibold text-gray-700">Pricing</h3>
                      <div>
                        <label className="block text-sm text-gray-600">Currency</label>
                        <input type="text" value={formData.pricing.currency} onChange={(e) => handleNestedChange('pricing', 'currency', e.target.value)} className="w-full rounded border p-2 bg-white" />
                      </div>
                      <div>
                        <label className="block text-sm text-gray-600">Base Price</label>
                        <input type="number" value={formData.pricing.basePrice} onChange={(e) => handleNestedChange('pricing', 'basePrice', e.target.value)} className="w-full rounded border p-2 bg-white" />
                      </div>
                      <div>
                        <label className="block text-sm text-gray-600">Discount %</label>
                        <input type="number" value={formData.pricing.discountPercentage} onChange={(e) => handleNestedChange('pricing', 'discountPercentage', e.target.value)} className="w-full rounded border p-2 bg-white" />
                      </div>
                      <div>
                        <label className="block text-sm font-bold text-gray-800">Final Price (Auto)</label>
                        <input readOnly type="number" value={formData.pricing.finalPrice} className="w-full rounded border p-2 bg-gray-200" />
                      </div>
                    </div>
                  </div>

                  {/* Media with File Upload */}
                  <div className="space-y-4 bg-gray-50 p-4 rounded-md">
                    <div className="flex justify-between items-center mb-2">
                      <h3 className="font-semibold text-gray-700">Media</h3>
                      {uploading && <span className="text-sm text-blue-600 animate-pulse">Uploading to Cloud...</span>}
                    </div>
                    
                    <div>
                      <label className="block text-sm text-gray-600">Thumbnail URL</label>
                      <div className="flex gap-2">
                        <input type="text" value={formData.media.thumbnailUrl} onChange={(e) => handleNestedChange('media', 'thumbnailUrl', e.target.value)} className="flex-1 rounded border p-2 bg-white" placeholder="https://..." />
                        <input type="text" value={mediaContexts.thumbnail} onChange={(e) => setMediaContexts({ ...mediaContexts, thumbnail: e.target.value })} className="w-48 rounded border p-2 bg-white text-sm" placeholder="e.g. theme=beach, category=hero" />
                        <label className="cursor-pointer flex items-center justify-center px-3 border border-gray-300 rounded bg-white hover:bg-gray-50 text-gray-600">
                          <Upload size={18} />
                          <input type="file" accept="image/*" className="hidden" onChange={(e) => handleImageUpload(e, 'thumbnail', null, mediaContexts.thumbnail)} disabled={uploading} />
                        </label>
                      </div>
                    </div>

                    <div>
                      <div className="flex justify-between items-center mb-1 mt-4">
                        <label className="block text-sm text-gray-600">Gallery URLs</label>
                        <button type="button" onClick={() => addArrayStringItem('galleryUrls')} className="text-blue-600 hover:text-blue-800"><Plus size={16}/></button>
                      </div>
                      {formData.media.galleryUrls.map((url, index) => (
                        <div key={index} className="flex gap-2 mb-2">
                          <input type="text" value={url} onChange={(e) => handleArrayStringChange('galleryUrls', index, e.target.value)} className="flex-1 rounded border p-2 bg-white text-sm" placeholder="https://..." />
                          <input type="text" value={mediaContexts.gallery[index] || ''} onChange={(e) => setMediaContexts({ ...mediaContexts, gallery: { ...mediaContexts.gallery, [index]: e.target.value } })} className="w-48 rounded border p-2 bg-white text-sm" placeholder="e.g. key=value, key2=value2" />
                          <label className="cursor-pointer flex items-center justify-center px-3 border border-gray-300 rounded bg-white hover:bg-gray-50 text-gray-600">
                            <Upload size={16} />
                            <input type="file" accept="image/*" className="hidden" onChange={(e) => handleImageUpload(e, 'gallery', index, mediaContexts.gallery[index])} disabled={uploading} />
                          </label>
                          <button type="button" onClick={() => removeArrayStringItem('galleryUrls', index)} className="bg-red-500 text-white p-2 rounded"><X size={16}/></button>
                        </div>
                      ))}
                    </div>

                    <div>
                      <label className="block text-sm text-gray-600 mt-4">Alt Text</label>
                      <input type="text" value={formData.media.altText} onChange={(e) => handleNestedChange('media', 'altText', e.target.value)} className="w-full rounded border p-2 bg-white" />
                    </div>
                  </div>

                  {/* Inclusions & Exclusions */}
                  <div className="grid grid-cols-2 gap-4">
                    <div className="bg-gray-50 p-4 rounded-md">
                      <div className="flex justify-between items-center mb-2">
                        <h3 className="font-semibold text-gray-700">Inclusions</h3>
                        <button type="button" onClick={() => addArrayStringItem('inclusions')} className="text-blue-600 hover:text-blue-800"><Plus size={16}/></button>
                      </div>
                      {formData.inclusions.map((item, index) => (
                        <div key={index} className="flex mb-2">
                          <input type="text" value={item} onChange={(e) => handleArrayStringChange('inclusions', index, e.target.value)} className="flex-1 rounded-l border p-1 bg-white text-sm" />
                          <button type="button" onClick={() => removeArrayStringItem('inclusions', index)} className="bg-red-500 text-white p-1 rounded-r"><X size={16}/></button>
                        </div>
                      ))}
                    </div>
                    <div className="bg-gray-50 p-4 rounded-md">
                      <div className="flex justify-between items-center mb-2">
                        <h3 className="font-semibold text-gray-700">Exclusions</h3>
                        <button type="button" onClick={() => addArrayStringItem('exclusions')} className="text-blue-600 hover:text-blue-800"><Plus size={16}/></button>
                      </div>
                      {formData.exclusions.map((item, index) => (
                        <div key={index} className="flex mb-2">
                          <input type="text" value={item} onChange={(e) => handleArrayStringChange('exclusions', index, e.target.value)} className="flex-1 rounded-l border p-1 bg-white text-sm" />
                          <button type="button" onClick={() => removeArrayStringItem('exclusions', index)} className="bg-red-500 text-white p-1 rounded-r"><X size={16}/></button>
                        </div>
                      ))}
                    </div>
                  </div>

                  {/* Itinerary */}
                  <div className="bg-gray-50 p-4 rounded-md">
                    <div className="flex justify-between items-center mb-4 border-b pb-2">
                      <div>
                        <h3 className="font-semibold text-gray-700">Itinerary</h3>
                        {formData.itinerary.length > 1 && (
                          <p className="text-[10px] text-gray-400 mt-0.5 flex items-center gap-1">
                            <GripVertical size={10} /> Drag the grip handle to reorder days
                          </p>
                        )}
                      </div>
                      <button type="button" onClick={addItineraryDay} className="flex items-center text-sm text-blue-600 hover:text-blue-800 font-medium">
                        <Plus size={16} className="mr-1"/> Add Day
                      </button>
                    </div>
                    <div className="space-y-3">
                      {formData.itinerary.map((day, index) => (
                        <div
                          key={index}
                          draggable
                          onDragStart={() => { dragIndexRef.current = index; }}
                          onDragEnd={() => { dragIndexRef.current = null; setDragOverIndex(null); }}
                          onDragOver={e => { e.preventDefault(); if (dragIndexRef.current !== index) setDragOverIndex(index); }}
                          onDragLeave={() => setDragOverIndex(null)}
                          onDrop={e => {
                            e.preventDefault();
                            const from = dragIndexRef.current;
                            const to = index;
                            if (from === null || from === to) { setDragOverIndex(null); return; }
                            const reordered = [...formData.itinerary];
                            const [moved] = reordered.splice(from, 1);
                            reordered.splice(to, 0, moved);
                            // renumber days sequentially
                            const renumbered = reordered.map((d, i) => ({ ...d, day: i + 1 }));
                            setFormData(prev => ({ ...prev, itinerary: renumbered }));
                            dragIndexRef.current = null;
                            setDragOverIndex(null);
                          }}
                          style={{
                            borderTop: dragOverIndex === index ? '3px solid #3b82f6' : '3px solid transparent',
                            opacity: dragIndexRef.current === index ? 0.45 : 1,
                            transition: 'border-color 0.12s ease, opacity 0.12s ease',
                          }}
                          className="border border-gray-200 bg-white rounded-lg relative"
                        >
                          {/* Header row: grip + day label + delete */}
                          <div
                            className="flex items-center gap-2 px-3 pt-3 pb-2 border-b border-gray-100 select-none"
                            style={{ cursor: 'grab' }}
                          >
                            <GripVertical size={16} className="text-gray-300 flex-shrink-0" style={{ cursor: 'grab' }} />
                            <span className="flex-1 font-bold text-gray-800 text-sm">Day {day.day}</span>
                            <button
                              type="button"
                              onClick={() => removeItineraryDay(index)}
                              className="text-red-400 hover:text-red-600 hover:bg-red-50 p-1 rounded"
                              onMouseDown={e => e.stopPropagation()}
                            >
                              <X size={15}/>
                            </button>
                          </div>

                          {/* Content */}
                          <div className="p-3">
                            <input
                              type="text"
                              placeholder="Day Title (e.g. Arrival in Kochi)"
                              value={day.title}
                              onChange={(e) => handleItineraryChange(index, 'title', e.target.value)}
                              className="w-full mb-2 p-2 border rounded text-sm"
                            />
                            <label className="block text-xs font-medium text-gray-500 mb-1">Activities</label>
                            <ReactQuill
                              theme="snow"
                              value={day.activities}
                              onChange={(value) => handleItineraryQuillChange(index, value)}
                              modules={quillModules}
                              formats={quillFormats}
                              className="bg-white rounded border text-sm"
                            />
                          </div>
                        </div>
                      ))}
                    </div>
                  </div>

                  {/* Special Notes */}
                  <div className="bg-gray-50 p-4 rounded-md">
                    <h3 className="font-semibold text-gray-700 mb-2 text-red-600">Special Notes / Conditions</h3>
                    <ReactQuill 
                      theme="snow"
                      value={formData.specialNotes} 
                      onChange={(value) => handleQuillChange('specialNotes', value)}
                      modules={quillModules}
                      formats={quillFormats}
                      placeholder="Enter special notes or conditions here..."
                      className="bg-white rounded-md overflow-hidden"
                    />
                  </div>

                  {/* Send PDF to Client — only in Custom Package mode */}
                  {isCustomPackage && (
                    <div className="rounded-xl border border-dashed border-indigo-300 bg-indigo-50 overflow-hidden">
                      <button
                        type="button"
                        onClick={() => setSendPdfPanelOpen(o => !o)}
                        className="w-full flex items-center justify-between px-4 py-3 text-indigo-700 hover:bg-indigo-100 transition-colors"
                      >
                        <span className="flex items-center gap-2 font-semibold text-sm">
                          <FileText size={16} />
                          📧 Send Package PDF to Client
                        </span>
                        {sendPdfPanelOpen ? <ChevronUp size={16} /> : <ChevronDown size={16} />}
                      </button>

                      {sendPdfPanelOpen && (
                        <div className="px-4 pb-5 pt-2 border-t border-indigo-200 space-y-3">
                          <p className="text-xs text-indigo-600">
                            Fill in the client details below. The package PDF will be generated and emailed to them directly.
                            <span className="block mt-1 font-semibold text-indigo-700">⚠ Save the custom package first before sending.</span>
                          </p>

                          <div className="grid grid-cols-2 gap-3">
                            <div>
                              <label className="block text-xs font-semibold text-gray-600 mb-1">First Name <span className="text-red-500">*</span></label>
                              <input
                                id="client-first-name"
                                type="text"
                                value={clientInfo.firstName}
                                onChange={e => setClientInfo(p => ({ ...p, firstName: e.target.value }))}
                                placeholder="Rahul"
                                className="w-full rounded border border-indigo-200 bg-white p-2 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-300"
                              />
                            </div>
                            <div>
                              <label className="block text-xs font-semibold text-gray-600 mb-1">Last Name</label>
                              <input
                                id="client-last-name"
                                type="text"
                                value={clientInfo.lastName}
                                onChange={e => setClientInfo(p => ({ ...p, lastName: e.target.value }))}
                                placeholder="Sharma"
                                className="w-full rounded border border-indigo-200 bg-white p-2 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-300"
                              />
                            </div>
                          </div>

                          <div>
                            <label className="block text-xs font-semibold text-gray-600 mb-1">Email Address <span className="text-red-500">*</span></label>
                            <input
                              id="client-email"
                              type="email"
                              value={clientInfo.email}
                              onChange={e => setClientInfo(p => ({ ...p, email: e.target.value }))}
                              placeholder="rahul@example.com"
                              className="w-full rounded border border-indigo-200 bg-white p-2 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-300"
                            />
                          </div>

                          <div className="grid grid-cols-2 gap-3">
                            <div>
                              <label className="block text-xs font-semibold text-gray-600 mb-1">Phone <span className="text-red-500">*</span></label>
                              <input
                                id="client-phone"
                                type="tel"
                                value={clientInfo.phone}
                                onChange={e => setClientInfo(p => ({ ...p, phone: e.target.value }))}
                                placeholder="9876543210"
                                className="w-full rounded border border-indigo-200 bg-white p-2 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-300"
                              />
                            </div>
                            <div>
                              <label className="block text-xs font-semibold text-gray-600 mb-1">Location / City <span className="text-red-500">*</span></label>
                              <input
                                id="client-location"
                                type="text"
                                value={clientInfo.location}
                                onChange={e => setClientInfo(p => ({ ...p, location: e.target.value }))}
                                placeholder="Mumbai"
                                className="w-full rounded border border-indigo-200 bg-white p-2 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-300"
                              />
                            </div>
                          </div>

                          <button
                            id="send-pdf-btn"
                            type="button"
                            onClick={handleSendPdf}
                            disabled={sendingPdf}
                            className="w-full flex items-center justify-center gap-2 px-4 py-2.5 rounded-lg bg-indigo-600 text-white text-sm font-semibold hover:bg-indigo-700 disabled:opacity-50 transition-colors"
                          >
                            {sendingPdf
                              ? <><span className="animate-spin inline-block w-4 h-4 border-2 border-white border-t-transparent rounded-full" /> Sending PDF…</>
                              : <><FileText size={15} /> Send PDF via Email</>}
                          </button>
                        </div>
                      )}
                    </div>
                  )}

                  {/* Submit */}
                  <div className="flex justify-end gap-3 border-t pt-4">
                    {editingId && (
                      <button type="button" onClick={() => { setEditingId(null); setIsCustomPackage(false); setFormData({ ...defaultForm, packageCode: getNextPackageCode(packages) }); }} className="px-4 py-2 border border-gray-300 rounded-md text-gray-700 bg-white hover:bg-gray-50">
                        Cancel
                      </button>
                    )}
                    <button type="submit" disabled={uploading || saving} className={`flex items-center px-6 py-2 border border-transparent rounded-md text-white shadow-sm font-medium disabled:opacity-50 ${ isCustomPackage ? 'bg-purple-600 hover:bg-purple-700' : 'bg-blue-600 hover:bg-blue-700' }`}>
                      {saving ? 'Saving...' : (editingId ? 'Update Package' : (isCustomPackage ? '✦ Save Custom Package' : 'Save Package'))}
                    </button>
                  </div>
                </form>
              </div>

              {/* List Section */}
              <div className="lg:w-1/2 bg-white rounded-lg shadow-sm border border-gray-200 overflow-hidden flex flex-col h-[calc(100vh-8rem)]">
                <div className="p-4 border-b bg-gray-50 space-y-3">
                  <div className="flex items-center justify-between">
                    <h2 className="text-lg font-bold text-gray-900">All Packages</h2>
                    {packageSearch && (
                      <span className="text-xs text-gray-500">
                        {packages.filter(pkg => {
                          const q = packageSearch.toLowerCase();
                          return (
                            pkg.packageCode?.toLowerCase().includes(q) ||
                            pkg.title?.toLowerCase().includes(q) ||
                            pkg.destination?.toLowerCase().includes(q) ||
                            pkg.packageType?.toLowerCase().includes(q) ||
                            pkg.status?.toLowerCase().includes(q)
                          );
                        }).length} of {packages.length} shown
                      </span>
                    )}
                  </div>
                  <div className="relative">
                    <Search size={15} className="absolute left-3 top-1/2 -translate-y-1/2 text-gray-400 pointer-events-none" />
                    <input
                      id="package-list-search"
                      type="text"
                      value={packageSearch}
                      onChange={e => setPackageSearch(e.target.value)}
                      placeholder="Search by code, name, destination, type…"
                      className="w-full pl-9 pr-8 py-2 text-sm border border-gray-300 rounded-md bg-white focus:outline-none focus:ring-2 focus:ring-blue-300 focus:border-blue-400"
                    />
                    {packageSearch && (
                      <button
                        type="button"
                        onClick={() => setPackageSearch('')}
                        className="absolute right-2 top-1/2 -translate-y-1/2 text-gray-400 hover:text-gray-600"
                      >
                        <X size={14} />
                      </button>
                    )}
                  </div>
                </div>
                <div className="overflow-y-auto flex-1 p-4 space-y-4">
                  {(() => {
                    const q = packageSearch.toLowerCase().trim();
                    const filtered = q
                      ? packages.filter(pkg =>
                          pkg.packageCode?.toLowerCase().includes(q) ||
                          pkg.title?.toLowerCase().includes(q) ||
                          pkg.destination?.toLowerCase().includes(q) ||
                          pkg.packageType?.toLowerCase().includes(q) ||
                          pkg.status?.toLowerCase().includes(q)
                        )
                      : packages;

                    if (filtered.length === 0) {
                      return (
                        <div className="flex flex-col items-center justify-center h-full text-gray-400 py-16">
                          <Search size={32} className="mb-3 opacity-40" />
                          <p className="text-sm font-medium">No packages match &ldquo;{packageSearch}&rdquo;</p>
                          <button
                            type="button"
                            onClick={() => setPackageSearch('')}
                            className="mt-2 text-xs text-blue-500 hover:underline"
                          >Clear search</button>
                        </div>
                      );
                    }

                    return filtered.map((pkg) => (
                      <div
                        key={pkg.id}
                        className="group relative flex gap-0 rounded-xl border border-gray-200 bg-white overflow-hidden"
                        style={{
                          height: '110px',
                          transition: 'transform 0.18s ease, box-shadow 0.18s ease, border-color 0.18s ease',
                        }}
                        onMouseEnter={e => {
                          e.currentTarget.style.transform = 'translateY(-3px)';
                          e.currentTarget.style.boxShadow = '0 8px 24px rgba(59,130,246,0.18)';
                          e.currentTarget.style.borderColor = '#3b82f6';
                        }}
                        onMouseLeave={e => {
                          e.currentTarget.style.transform = 'translateY(0)';
                          e.currentTarget.style.boxShadow = 'none';
                          e.currentTarget.style.borderColor = '#e5e7eb';
                        }}
                      >
                        {/* Blue left accent bar */}
                        <div
                          className="w-1 flex-shrink-0 bg-blue-500 opacity-0 group-hover:opacity-100"
                          style={{ transition: 'opacity 0.18s ease' }}
                        />

                        {/* Thumbnail — fixed 110×110 */}
                        <div className="w-[110px] h-[110px] flex-shrink-0 overflow-hidden">
                          <img
                            src={pkg.media?.thumbnailUrl || 'https://via.placeholder.com/150'}
                            alt={pkg.title}
                            className="w-full h-full object-cover"
                            style={{ transition: 'transform 0.25s ease' }}
                            onMouseEnter={e => { e.currentTarget.style.transform = 'scale(1.07)'; }}
                            onMouseLeave={e => { e.currentTarget.style.transform = 'scale(1)'; }}
                          />
                        </div>

                        {/* Content — fills remaining width, vertically centred */}
                        <div className="flex-1 min-w-0 flex flex-col justify-center px-4 pr-20">
                          {/* Badges row */}
                          <div className="flex items-center gap-1.5 mb-1 flex-wrap">
                            <span className={`text-[10px] font-bold px-2 py-0.5 rounded tracking-wide font-mono ${ pkg.packageCode?.startsWith('CUSPKG') ? 'bg-purple-100 text-purple-700' : 'bg-gray-100 text-gray-500' }`}>
                              {pkg.packageCode}
                              {pkg.packageCode?.startsWith('CUSPKG') && <span className="ml-1">✦</span>}
                            </span>
                            <span className={`text-[10px] font-bold px-2 py-0.5 rounded ${pkg.status === 'ACTIVE' ? 'bg-green-100 text-green-700' : 'bg-red-100 text-red-700'}`}>
                              {pkg.status}
                            </span>
                            {pkg.packageType && (
                              <span className="text-[10px] font-semibold bg-blue-50 text-blue-600 px-2 py-0.5 rounded">
                                {pkg.packageType}
                              </span>
                            )}
                          </div>

                          {/* Title — single line, clipped */}
                          <h3
                            className="font-bold text-gray-900 text-sm leading-tight"
                            style={{ overflow: 'hidden', whiteSpace: 'nowrap', textOverflow: 'ellipsis' }}
                          >
                            {pkg.title}
                          </h3>

                          {/* Destination — single line, clipped */}
                          <p
                            className="text-xs text-gray-500 mt-0.5"
                            style={{ overflow: 'hidden', whiteSpace: 'nowrap', textOverflow: 'ellipsis' }}
                          >
                            {pkg.destination}
                          </p>

                          {/* Price + duration */}
                          <div className="flex gap-3 text-xs font-semibold mt-1.5">
                            <span className="text-blue-600">{pkg.pricing?.currency} {formatCurrency(pkg.pricing?.finalPrice)}</span>
                            <span className="text-gray-500">{pkg.duration?.days}D / {pkg.duration?.nights}N</span>
                          </div>
                        </div>

                        {/* Action buttons — vertically centred on the right */}
                        <div className="absolute right-3 top-1/2 -translate-y-1/2 flex flex-col gap-1.5">
                          <button
                            onClick={() => handleEdit(pkg)}
                            className="text-blue-500 hover:text-white hover:bg-blue-500 p-1.5 rounded-md border border-blue-200 hover:border-blue-500"
                            style={{ transition: 'all 0.15s ease' }}
                          >
                            <Edit2 size={15} />
                          </button>
                          <button
                            onClick={() => handleDelete(pkg.id)}
                            className="text-red-400 hover:text-white hover:bg-red-500 p-1.5 rounded-md border border-red-200 hover:border-red-500"
                            style={{ transition: 'all 0.15s ease' }}
                          >
                            <Trash2 size={15} />
                          </button>
                        </div>
                      </div>
                    ));
                  })()}
                </div>
              </div>
            </>
          )}
          
        </div>
      </main>
    </div>
  );
};

export default Admin;
