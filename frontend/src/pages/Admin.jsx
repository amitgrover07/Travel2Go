import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { LogOut, Plus, Edit2, Trash2, X, Upload } from 'lucide-react';
import api from '../services/api';

const defaultForm = {
  packageCode: '',
  title: '',
  destination: '',
  status: 'ACTIVE',
  overview: '',
  duration: { days: '', nights: '' },
  pricing: { currency: 'INR', basePrice: '', discountPercentage: '', finalPrice: '' },
  media: { thumbnailUrl: '', galleryUrls: [], altText: '' },
  inclusions: [],
  exclusions: [],
  itinerary: [],
  version: 0
};

const Admin = () => {
  const [packages, setPackages] = useState([]);
  const [formData, setFormData] = useState(defaultForm);
  const [editingId, setEditingId] = useState(null);
  const [uploading, setUploading] = useState(false);
  const navigate = useNavigate();

  useEffect(() => {
    fetchPackages();
  }, []);

  const fetchPackages = async () => {
    try {
      const response = await api.get('/packages/all');
      setPackages(response.data);
    } catch (error) {
      if (error.response && error.response.status === 403) {
        handleLogout();
      }
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

  const handleImageUpload = async (e, type, index = null) => {
    const file = e.target.files[0];
    if (!file) return;

    setUploading(true);
    const formDataPayload = new FormData();
    formDataPayload.append('file', file);

    try {
      const response = await api.post('/media/upload', formDataPayload, {
        headers: { 'Content-Type': 'multipart/form-data' }
      });
      const url = response.data.url;

      if (type === 'thumbnail') {
        handleNestedChange('media', 'thumbnailUrl', url);
      } else if (type === 'gallery') {
        handleArrayStringChange('galleryUrls', index, url);
      }
    } catch (error) {
      console.error('Error uploading image', error);
      alert('Failed to upload image to Google Cloud Storage. Ensure credentials are valid.');
    } finally {
      setUploading(false);
    }
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (uploading) {
      alert("Please wait for image upload to finish.");
      return;
    }
    try {
      if (editingId) {
        await api.put(`/packages/${editingId}`, formData);
      } else {
        await api.post('/packages', formData);
      }
      setFormData(defaultForm);
      setEditingId(null);
      fetchPackages();
    } catch (error) {
      console.error('Error saving package', error);
      if (error.response && error.response.status === 409) {
        alert(error.response.data.message || 'This package was modified by another user. Please refresh the page and try again.');
      } else if (error.response && error.response.status === 403) {
        handleLogout();
      }
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
      version: pkg.version || 0
    });
  };

  const handleDelete = async (id) => {
    if (window.confirm('Are you sure you want to delete this package?')) {
      try {
        await api.delete(`/packages/${id}`);
        fetchPackages();
      } catch (error) {
        console.error('Error deleting package', error);
        if (error.response && error.response.status === 403) handleLogout();
      }
    }
  };

  return (
    <div className="min-h-screen bg-gray-100">
      <nav className="bg-white shadow-sm sticky top-0 z-10">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
          <div className="flex justify-between h-16 items-center">
            <h1 className="text-xl font-bold text-gray-900">Admin Dashboard</h1>
            <button onClick={handleLogout} className="flex items-center text-gray-600 hover:text-gray-900">
              <LogOut className="h-5 w-5 mr-1" /> Logout
            </button>
          </div>
        </div>
      </nav>

      <main className="max-w-7xl mx-auto py-6 sm:px-6 lg:px-8">
        <div className="flex flex-col lg:flex-row gap-6">
          
          {/* Form Section */}
          <div className="lg:w-1/2 bg-white p-6 rounded-lg shadow-sm border border-gray-200 overflow-y-auto max-h-[calc(100vh-8rem)]">
            <h2 className="text-xl font-bold text-gray-900 mb-6 border-b pb-2">
              {editingId ? 'Edit Package' : 'Add New Package'}
            </h2>
            <form onSubmit={handleSubmit} className="space-y-6">
              
              {/* Basic Info */}
              <div className="space-y-4 bg-gray-50 p-4 rounded-md">
                <h3 className="font-semibold text-gray-700">Basic Info</h3>
                <div className="grid grid-cols-2 gap-4">
                  <div>
                    <label className="block text-sm font-medium text-gray-700">Package Code</label>
                    <input required type="text" name="packageCode" value={formData.packageCode} onChange={handleTopLevelChange} className="mt-1 block w-full rounded-md border-gray-300 shadow-sm focus:border-blue-500 focus:ring-blue-500 p-2 border bg-white" placeholder="e.g. PKG-001" />
                  </div>
                  <div>
                    <label className="block text-sm font-medium text-gray-700">Status</label>
                    <select name="status" value={formData.status} onChange={handleTopLevelChange} className="mt-1 block w-full rounded-md border-gray-300 shadow-sm focus:border-blue-500 focus:ring-blue-500 p-2 border bg-white">
                      <option value="ACTIVE">ACTIVE</option>
                      <option value="INACTIVE">INACTIVE</option>
                    </select>
                  </div>
                </div>
                <div>
                  <label className="block text-sm font-medium text-gray-700">Title</label>
                  <input required type="text" name="title" value={formData.title} onChange={handleTopLevelChange} className="mt-1 block w-full rounded-md border-gray-300 shadow-sm focus:border-blue-500 focus:ring-blue-500 p-2 border bg-white" />
                </div>
                <div>
                  <label className="block text-sm font-medium text-gray-700">Destination</label>
                  <input required type="text" name="destination" value={formData.destination} onChange={handleTopLevelChange} className="mt-1 block w-full rounded-md border-gray-300 shadow-sm focus:border-blue-500 focus:ring-blue-500 p-2 border bg-white" />
                </div>
                <div>
                  <label className="block text-sm font-medium text-gray-700">Overview</label>
                  <textarea required name="overview" value={formData.overview} onChange={handleTopLevelChange} rows={3} className="mt-1 block w-full rounded-md border-gray-300 shadow-sm focus:border-blue-500 focus:ring-blue-500 p-2 border bg-white" />
                </div>
              </div>

              {/* Duration & Pricing */}
              <div className="grid grid-cols-2 gap-4">
                <div className="space-y-4 bg-gray-50 p-4 rounded-md">
                  <h3 className="font-semibold text-gray-700">Duration</h3>
                  <div>
                    <label className="block text-sm text-gray-600">Days</label>
                    <input required type="number" value={formData.duration.days} onChange={(e) => handleNestedChange('duration', 'days', e.target.value)} className="w-full rounded border p-2 bg-white" />
                  </div>
                  <div>
                    <label className="block text-sm text-gray-600">Nights</label>
                    <input required type="number" value={formData.duration.nights} onChange={(e) => handleNestedChange('duration', 'nights', e.target.value)} className="w-full rounded border p-2 bg-white" />
                  </div>
                </div>

                <div className="space-y-4 bg-gray-50 p-4 rounded-md">
                  <h3 className="font-semibold text-gray-700">Pricing</h3>
                  <div>
                    <label className="block text-sm text-gray-600">Currency</label>
                    <input required type="text" value={formData.pricing.currency} onChange={(e) => handleNestedChange('pricing', 'currency', e.target.value)} className="w-full rounded border p-2 bg-white" />
                  </div>
                  <div>
                    <label className="block text-sm text-gray-600">Base Price</label>
                    <input required type="number" value={formData.pricing.basePrice} onChange={(e) => handleNestedChange('pricing', 'basePrice', e.target.value)} className="w-full rounded border p-2 bg-white" />
                  </div>
                  <div>
                    <label className="block text-sm text-gray-600">Discount %</label>
                    <input required type="number" value={formData.pricing.discountPercentage} onChange={(e) => handleNestedChange('pricing', 'discountPercentage', e.target.value)} className="w-full rounded border p-2 bg-white" />
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
                    <input required type="text" value={formData.media.thumbnailUrl} onChange={(e) => handleNestedChange('media', 'thumbnailUrl', e.target.value)} className="flex-1 rounded border p-2 bg-white" placeholder="https://..." />
                    <label className="cursor-pointer flex items-center justify-center px-3 border border-gray-300 rounded bg-white hover:bg-gray-50 text-gray-600">
                      <Upload size={18} />
                      <input type="file" accept="image/*" className="hidden" onChange={(e) => handleImageUpload(e, 'thumbnail')} disabled={uploading} />
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
                      <label className="cursor-pointer flex items-center justify-center px-3 border border-gray-300 rounded bg-white hover:bg-gray-50 text-gray-600">
                        <Upload size={16} />
                        <input type="file" accept="image/*" className="hidden" onChange={(e) => handleImageUpload(e, 'gallery', index)} disabled={uploading} />
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
                  <h3 className="font-semibold text-gray-700">Itinerary</h3>
                  <button type="button" onClick={addItineraryDay} className="flex items-center text-sm text-blue-600 hover:text-blue-800 font-medium">
                    <Plus size={16} className="mr-1"/> Add Day
                  </button>
                </div>
                <div className="space-y-4">
                  {formData.itinerary.map((day, index) => (
                    <div key={index} className="border border-gray-200 bg-white p-3 rounded relative">
                      <button type="button" onClick={() => removeItineraryDay(index)} className="absolute top-2 right-2 text-red-500 hover:text-red-700"><X size={16}/></button>
                      <h4 className="font-bold text-gray-800 mb-2">Day {day.day}</h4>
                      <input type="text" placeholder="Day Title (e.g. Arrival in Kochi)" value={day.title} onChange={(e) => handleItineraryChange(index, 'title', e.target.value)} className="w-full mb-2 p-2 border rounded text-sm" />
                      <textarea placeholder="Activities for the day..." value={day.activities} onChange={(e) => handleItineraryChange(index, 'activities', e.target.value)} rows={2} className="w-full p-2 border rounded text-sm" />
                    </div>
                  ))}
                </div>
              </div>

              {/* Submit */}
              <div className="flex justify-end gap-3 border-t pt-4">
                {editingId && (
                  <button type="button" onClick={() => { setEditingId(null); setFormData(defaultForm); }} className="px-4 py-2 border border-gray-300 rounded-md text-gray-700 bg-white hover:bg-gray-50">
                    Cancel
                  </button>
                )}
                <button type="submit" disabled={uploading} className="flex items-center px-6 py-2 border border-transparent rounded-md text-white bg-blue-600 hover:bg-blue-700 shadow-sm font-medium disabled:bg-blue-400">
                  {editingId ? 'Update Package' : 'Save Package'}
                </button>
              </div>
            </form>
          </div>

          {/* List Section */}
          <div className="lg:w-1/2 bg-white rounded-lg shadow-sm border border-gray-200 overflow-hidden flex flex-col h-[calc(100vh-8rem)]">
            <div className="p-4 border-b bg-gray-50">
              <h2 className="text-lg font-bold text-gray-900">All Packages</h2>
            </div>
            <div className="overflow-y-auto flex-1 p-4 space-y-4">
              {packages.map((pkg) => (
                <div key={pkg.id} className="border border-gray-200 rounded-lg p-4 hover:shadow-md transition-shadow relative">
                  <div className="absolute top-4 right-4 flex space-x-2">
                    <button onClick={() => handleEdit(pkg)} className="text-blue-600 hover:bg-blue-50 p-1 rounded">
                      <Edit2 size={18} />
                    </button>
                    <button onClick={() => handleDelete(pkg.id)} className="text-red-600 hover:bg-red-50 p-1 rounded">
                      <Trash2 size={18} />
                    </button>
                  </div>
                  
                  <div className="flex gap-4">
                    <div className="w-24 h-24 flex-shrink-0">
                      <img src={pkg.media?.thumbnailUrl || 'https://via.placeholder.com/150'} alt={pkg.title} className="w-full h-full object-cover rounded-md border" />
                    </div>
                    <div className="flex-1 pr-16">
                      <div className="flex items-center gap-2 mb-1">
                        <span className="text-xs font-bold bg-gray-100 px-2 py-1 rounded text-gray-600">{pkg.packageCode}</span>
                        <span className={`text-xs font-bold px-2 py-1 rounded ${pkg.status === 'ACTIVE' ? 'bg-green-100 text-green-800' : 'bg-red-100 text-red-800'}`}>
                          {pkg.status}
                        </span>
                      </div>
                      <h3 className="font-bold text-gray-900">{pkg.title}</h3>
                      <p className="text-sm text-gray-500 mb-2">{pkg.destination}</p>
                      
                      <div className="flex gap-4 text-sm font-medium">
                        <span className="text-blue-600">{pkg.pricing?.currency} {pkg.pricing?.finalPrice}</span>
                        <span className="text-gray-600">{pkg.duration?.days}D/{pkg.duration?.nights}N</span>
                      </div>
                    </div>
                  </div>
                </div>
              ))}
            </div>
          </div>
          
        </div>
      </main>
    </div>
  );
};

export default Admin;
