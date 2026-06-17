import React, { useState, useEffect } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { Search, Copy, ExternalLink, ArrowLeft, Loader2, Image as ImageIcon, Tag, Calendar, Database, Trash2, Plus, X, Upload } from 'lucide-react';
import toast from 'react-hot-toast';
import api from '../services/api';
import { GallerySkeleton } from '../components/SkeletonLoader';

const ImageGallery = () => {
  const [images, setImages] = useState([]);
  const [filteredImages, setFilteredImages] = useState([]);
  const [loading, setLoading] = useState(true);
  const [searchTerm, setSearchTerm] = useState('');
  const [showUploadModal, setShowUploadModal] = useState(false);
  const [uploading, setUploading] = useState(false);
  const [newImage, setNewImage] = useState({ file: null, preview: null });
  const [customMetadata, setCustomMetadata] = useState([{ key: 'context', value: '' }]);
  
  const navigate = useNavigate();

  useEffect(() => {
    fetchImages();
  }, []);

  const fetchImages = async () => {
    try {
      setLoading(true);
      const response = await api.get('/media/all');
      const sorted = response.data.sort((a, b) => (b.updatedAt || 0) - (a.updatedAt || 0));
      setImages(sorted);
      setFilteredImages(sorted);
    } catch (error) {
      console.error('Error fetching images', error);
      toast.error('Failed to load images');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    const term = searchTerm.toLowerCase();
    const filtered = images.filter(img => {
      if (img.name.toLowerCase().includes(term)) return true;
      if (img.metadata) {
        return Object.entries(img.metadata).some(([key, value]) => 
          key.toLowerCase().includes(term) || value.toLowerCase().includes(term)
        );
      }
      return false;
    });
    setFilteredImages(filtered);
  }, [searchTerm, images]);

  const copyToClipboard = (text) => {
    navigator.clipboard.writeText(text);
    toast.success('URL copied to clipboard!');
  };

  const handleDelete = async (fileName) => {
    if (!window.confirm('Are you sure you want to delete this image? This action cannot be undone.')) return;
    
    try {
      await api.delete(`/media/${fileName}`);
      toast.success('Image deleted successfully');
      setImages(prev => prev.filter(img => img.name !== fileName));
    } catch (error) {
      console.error('Error deleting image', error);
      toast.error('Failed to delete image');
    }
  };

  const handleFileChange = (e) => {
    const file = e.target.files[0];
    if (file) {
      setNewImage({
        file,
        preview: URL.createObjectURL(file)
      });
    }
  };

  const handleAddMetadata = () => {
    setCustomMetadata([...customMetadata, { key: '', value: '' }]);
  };

  const handleMetadataChange = (index, field, value) => {
    const newMetadata = [...customMetadata];
    newMetadata[index][field] = value;
    setCustomMetadata(newMetadata);
  };

  const handleRemoveMetadata = (index) => {
    setCustomMetadata(customMetadata.filter((_, i) => i !== index));
  };

  const handleUpload = async (e) => {
    e.preventDefault();
    if (!newImage.file) {
      toast.error('Please select an image first');
      return;
    }

    setUploading(true);
    const formData = new FormData();
    formData.append('file', newImage.file);

    // Build context string from metadata
    const contextString = customMetadata
      .filter(m => m.key.trim() && m.value.trim())
      .map(m => `${m.key.trim()}=${m.value.trim()}`)
      .join(',');
    
    if (contextString) {
      formData.append('context', contextString);
    }

    try {
      await api.post('/media/upload', formData, {
        headers: { 'Content-Type': 'multipart/form-data' }
      });
      toast.success('Image uploaded successfully!');
      setShowUploadModal(false);
      setNewImage({ file: null, preview: null });
      setCustomMetadata([{ key: 'context', value: '' }]);
      fetchImages();
    } catch (error) {
      console.error('Upload error', error);
      toast.error('Failed to upload image');
    } finally {
      setUploading(false);
    }
  };

  const formatSize = (bytes) => {
    if (!bytes) return '0 B';
    const k = 1024;
    const sizes = ['B', 'KB', 'MB', 'GB'];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i];
  };

  const formatDate = (timestamp) => {
    if (!timestamp) return 'Unknown';
    return new Date(timestamp).toLocaleDateString('en-IN', {
      day: '2-digit',
      month: 'short',
      year: 'numeric'
    });
  };

  return (
    <div className="min-h-screen bg-gray-50 pb-12">
      {/* Header */}
      <nav className="bg-white shadow-sm sticky top-0 z-10 border-b border-gray-200">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
          <div className="flex justify-between h-16 items-center">
            <div className="flex items-center space-x-4">
              <button 
                onClick={() => navigate('/admin')}
                className="p-2 rounded-full hover:bg-gray-100 transition-colors text-gray-600"
              >
                <ArrowLeft size={20} />
              </button>
              <h1 className="text-xl font-bold text-gray-900 flex items-center gap-2">
                <ImageIcon className="text-blue-600" /> Media Gallery
              </h1>
            </div>
            
            <div className="flex-1 max-w-md mx-8">
              <div className="relative">
                <Search className="absolute left-3 top-1/2 -translate-y-1/2 text-gray-400" size={18} />
                <input
                  type="text"
                  placeholder="Filter by name or metadata..."
                  className="w-full pl-10 pr-4 py-2 border border-gray-300 rounded-full focus:ring-2 focus:ring-blue-500 transition-all outline-none text-sm bg-gray-50 hover:bg-white"
                  value={searchTerm}
                  onChange={(e) => setSearchTerm(e.target.value)}
                />
              </div>
            </div>

            <div className="flex items-center space-x-3">
               <button 
                 onClick={() => setShowUploadModal(true)}
                 className="flex items-center gap-2 bg-blue-600 hover:bg-blue-700 text-white px-4 py-2 rounded-lg text-sm font-bold shadow-sm transition-all"
               >
                 <Plus size={18} /> Upload Image
               </button>
               <button 
                 onClick={fetchImages}
                 className="p-2 rounded-full hover:bg-gray-100 text-gray-600"
                 title="Refresh"
               >
                 <Database size={18} />
               </button>
            </div>
          </div>
        </div>
      </nav>

      {/* Content */}
      <main className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
        {loading ? (
          <GallerySkeleton />
        ) : filteredImages.length === 0 ? (
          <div className="text-center py-20 bg-white rounded-3xl border-2 border-dashed border-gray-200">
             <ImageIcon size={48} className="text-gray-300 mx-auto mb-4" />
             <h3 className="text-lg font-semibold text-gray-900">No images found</h3>
             <button 
                onClick={() => setShowUploadModal(true)}
                className="mt-4 text-blue-600 font-bold hover:underline"
             >
               Upload your first image
             </button>
          </div>
        ) : (
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-6">
            {filteredImages.map((img) => (
              <div key={img.name} className="group bg-white rounded-xl overflow-hidden shadow-sm hover:shadow-lg border border-gray-200 transition-all flex flex-col">
                <div className="relative aspect-video bg-gray-100 overflow-hidden">
                  <img src={img.url} alt={img.name} className="w-full h-full object-cover group-hover:scale-105 transition-transform duration-500" />
                  <div className="absolute inset-0 bg-black/50 opacity-0 group-hover:opacity-100 transition-opacity flex items-center justify-center gap-3">
                    <button onClick={() => copyToClipboard(img.url)} className="p-2 bg-white/20 rounded-full hover:bg-white/40 text-white" title="Copy URL">
                      <Copy size={18} />
                    </button>
                    <button onClick={() => handleDelete(img.name)} className="p-2 bg-red-500/20 rounded-full hover:bg-red-500/40 text-red-200" title="Delete Image">
                      <Trash2 size={18} />
                    </button>
                    <a href={img.url} target="_blank" rel="noreferrer" className="p-2 bg-white/20 rounded-full hover:bg-white/40 text-white">
                      <ExternalLink size={18} />
                    </a>
                  </div>
                </div>
                <div className="p-4 flex-1">
                  <h3 className="text-sm font-bold truncate text-gray-900 mb-1">{img.name}</h3>
                  <div className="flex items-center text-[10px] text-gray-400 gap-3 mb-3">
                    <span className="flex items-center gap-1"><Tag size={10} /> {formatSize(img.size)}</span>
                    <span className="flex items-center gap-1"><Calendar size={10} /> {formatDate(img.updatedAt)}</span>
                  </div>
                  {img.metadata && Object.keys(img.metadata).length > 0 && (
                    <div className="flex flex-wrap gap-1">
                      {Object.entries(img.metadata).map(([key, value]) => (
                        <span key={key} className="px-2 py-0.5 bg-blue-50 text-blue-600 rounded text-[9px] font-bold border border-blue-100">
                          {key}: {value}
                        </span>
                      ))}
                    </div>
                  )}
                </div>
              </div>
            ))}
          </div>
        )}
      </main>

      {/* Upload Modal */}
      {showUploadModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/60 backdrop-blur-sm">
          <div className="bg-white rounded-3xl shadow-2xl w-full max-w-2xl overflow-hidden animate-in fade-in zoom-in duration-200">
            <div className="flex justify-between items-center p-6 border-b">
              <h2 className="text-2xl font-extrabold text-gray-900">Upload Media</h2>
              <button onClick={() => setShowUploadModal(false)} className="p-2 hover:bg-gray-100 rounded-full transition-colors">
                <X size={24} className="text-gray-500" />
              </button>
            </div>

            <form onSubmit={handleUpload} className="p-8 space-y-6">
              <div className="grid grid-cols-1 md:grid-cols-2 gap-8">
                {/* File Select */}
                <div className="space-y-4">
                  <label className="block text-sm font-bold text-gray-700">Image Selection</label>
                  <div className="relative aspect-video rounded-2xl border-2 border-dashed border-gray-300 hover:border-blue-400 transition-colors flex flex-col items-center justify-center bg-gray-50 overflow-hidden">
                    {newImage.preview ? (
                      <img src={newImage.preview} alt="Preview" className="w-full h-full object-cover" />
                    ) : (
                      <div className="text-center p-4">
                        <Upload size={32} className="text-gray-400 mx-auto mb-2" />
                        <p className="text-xs text-gray-500">Click to choose or drag & drop</p>
                      </div>
                    )}
                    <input 
                      type="file" 
                      accept="image/*" 
                      onChange={handleFileChange} 
                      className="absolute inset-0 opacity-0 cursor-pointer"
                    />
                  </div>
                  {newImage.file && (
                    <p className="text-xs text-blue-600 font-medium truncate">{newImage.file.name}</p>
                  )}
                </div>

                {/* Metadata Section */}
                <div className="space-y-4">
                  <div className="flex justify-between items-center">
                    <label className="block text-sm font-bold text-gray-700">Custom Metadata</label>
                    <button 
                      type="button" 
                      onClick={handleAddMetadata}
                      className="text-xs font-bold text-blue-600 hover:text-blue-700 flex items-center gap-1"
                    >
                      <Plus size={14} /> Add Tag
                    </button>
                  </div>
                  <div className="space-y-3 max-h-[200px] overflow-y-auto pr-2">
                    {customMetadata.map((m, i) => (
                      <div key={i} className="flex gap-2 group">
                        <input 
                          type="text" 
                          placeholder="Key" 
                          value={m.key} 
                          onChange={(e) => handleMetadataChange(i, 'key', e.target.value)}
                          className="w-1/3 p-2 text-xs border rounded-lg outline-none focus:ring-1 focus:ring-blue-500"
                        />
                        <input 
                          type="text" 
                          placeholder="Value" 
                          value={m.value} 
                          onChange={(e) => handleMetadataChange(i, 'value', e.target.value)}
                          className="flex-1 p-2 text-xs border rounded-lg outline-none focus:ring-1 focus:ring-blue-500"
                        />
                        <button 
                          type="button" 
                          onClick={() => handleRemoveMetadata(i)}
                          className="p-2 text-red-500 opacity-0 group-hover:opacity-100 transition-opacity"
                        >
                          <X size={14} />
                        </button>
                      </div>
                    ))}
                  </div>
                </div>
              </div>

              <div className="flex justify-end gap-3 pt-6 border-t">
                <button 
                  type="button" 
                  onClick={() => setShowUploadModal(false)}
                  className="px-6 py-2.5 text-sm font-bold text-gray-600 hover:bg-gray-100 rounded-xl transition-colors"
                >
                  Cancel
                </button>
                <button 
                  type="submit" 
                  disabled={uploading}
                  className="flex items-center gap-2 bg-blue-600 hover:bg-blue-700 text-white px-8 py-2.5 rounded-xl text-sm font-bold shadow-lg shadow-blue-200 disabled:opacity-50 transition-all"
                >
                  {uploading ? (
                    <>
                      <Loader2 className="animate-spin" size={18} /> Uploading...
                    </>
                  ) : (
                    'Start Upload'
                  )}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
};

export default ImageGallery;
