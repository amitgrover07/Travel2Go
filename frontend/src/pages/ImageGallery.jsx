import React, { useState, useEffect } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { Search, Copy, ExternalLink, ArrowLeft, Loader2, Image as ImageIcon, Tag, Calendar, Database } from 'lucide-react';
import toast from 'react-hot-toast';
import api from '../services/api';

const ImageGallery = () => {
  const [images, setImages] = useState([]);
  const [filteredImages, setFilteredImages] = useState([]);
  const [loading, setLoading] = useState(true);
  const [searchTerm, setSearchTerm] = useState('');
  const navigate = useNavigate();

  useEffect(() => {
    fetchImages();
  }, []);

  const fetchImages = async () => {
    try {
      setLoading(true);
      const response = await api.get('/media/all');
      // Sort by updated time descending
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
      // Search in name
      if (img.name.toLowerCase().includes(term)) return true;
      
      // Search in metadata keys and values
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
      year: 'numeric',
      hour: '2-digit',
      minute: '2-digit'
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
                  placeholder="Filter by name or metadata (e.g. 'beach')..."
                  className="w-full pl-10 pr-4 py-2 border border-gray-300 rounded-full focus:ring-2 focus:ring-blue-500 focus:border-transparent transition-all outline-none text-sm bg-gray-50 hover:bg-white"
                  value={searchTerm}
                  onChange={(e) => setSearchTerm(e.target.value)}
                />
              </div>
            </div>

            <div className="flex items-center space-x-4">
               <span className="text-sm font-medium text-gray-500">
                 {filteredImages.length} {filteredImages.length === 1 ? 'item' : 'items'}
               </span>
               <button 
                 onClick={fetchImages}
                 className="p-2 rounded-full hover:bg-gray-100 transition-colors text-gray-600"
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
          <div className="flex flex-col items-center justify-center h-64 space-y-4">
            <Loader2 className="animate-spin text-blue-600" size={48} />
            <p className="text-gray-500 font-medium">Fetching your assets from cloud...</p>
          </div>
        ) : filteredImages.length === 0 ? (
          <div className="text-center py-20 bg-white rounded-3xl border-2 border-dashed border-gray-200 shadow-sm">
             <div className="bg-gray-50 w-20 h-20 rounded-full flex items-center justify-center mx-auto mb-4">
               <ImageIcon size={32} className="text-gray-300" />
             </div>
             <h3 className="text-lg font-semibold text-gray-900">No images found</h3>
             <p className="text-gray-500 max-w-xs mx-auto mt-2">
               {searchTerm ? `No results match "${searchTerm}"` : "Try uploading some images in the Admin dashboard."}
             </p>
             {searchTerm && (
               <button 
                 onClick={() => setSearchTerm('')}
                 className="mt-6 text-blue-600 font-semibold hover:underline"
               >
                 Clear search
               </button>
             )}
          </div>
        ) : (
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-8">
            {filteredImages.map((img, idx) => (
              <div 
                key={img.name + idx} 
                className="group bg-white rounded-2xl overflow-hidden shadow-sm hover:shadow-xl transition-all duration-300 border border-gray-100 flex flex-col"
              >
                {/* Image Container */}
                <div className="relative aspect-video overflow-hidden bg-gray-100">
                  <img 
                    src={img.url} 
                    alt={img.name}
                    className="w-full h-full object-cover group-hover:scale-105 transition-transform duration-500"
                    loading="lazy"
                    onError={(e) => {
                      e.target.src = 'https://via.placeholder.com/400x225?text=Image+Not+Found';
                    }}
                  />
                  <div className="absolute inset-0 bg-black/40 opacity-0 group-hover:opacity-100 transition-opacity flex items-center justify-center gap-4">
                    <button 
                      onClick={() => copyToClipboard(img.url)}
                      className="p-3 bg-white/20 backdrop-blur-md rounded-full hover:bg-white/30 text-white transition-all transform hover:scale-110"
                      title="Copy URL"
                    >
                      <Copy size={20} />
                    </button>
                    <a 
                      href={img.url} 
                      target="_blank" 
                      rel="noopener noreferrer"
                      className="p-3 bg-white/20 backdrop-blur-md rounded-full hover:bg-white/30 text-white transition-all transform hover:scale-110"
                      title="Open Original"
                    >
                      <ExternalLink size={20} />
                    </a>
                  </div>
                  <div className="absolute bottom-2 left-2 right-2 flex gap-1 flex-wrap">
                    {img.metadata && Object.entries(img.metadata).slice(0, 3).map(([key, value]) => (
                      <span key={key} className="px-2 py-0.5 bg-black/60 backdrop-blur-sm text-[10px] text-white rounded-md font-medium">
                        {value}
                      </span>
                    ))}
                  </div>
                </div>

                {/* Info Section */}
                <div className="p-4 flex-1 flex flex-col">
                  <div className="mb-3">
                    <h3 className="text-sm font-bold text-gray-900 truncate mb-1" title={img.name}>
                      {img.name}
                    </h3>
                    <div className="flex items-center text-[11px] text-gray-400 space-x-3">
                      <span className="flex items-center gap-1">
                        <Tag size={12} /> {formatSize(img.size)}
                      </span>
                      <span className="flex items-center gap-1">
                        <Calendar size={12} /> {formatDate(img.updatedAt)}
                      </span>
                    </div>
                  </div>

                  <div className="mt-auto pt-3 border-t border-gray-50">
                    <div className="flex items-center justify-between">
                      <p className="text-[10px] font-mono text-gray-400 truncate max-w-[150px]">
                        {img.url}
                      </p>
                      <button 
                         onClick={() => copyToClipboard(img.url)}
                         className="text-blue-600 hover:text-blue-700 text-xs font-bold"
                      >
                        Copy URL
                      </button>
                    </div>
                  </div>
                  
                  {/* Metadata Detail */}
                  {img.metadata && Object.keys(img.metadata).length > 0 && (
                    <div className="mt-3 flex flex-wrap gap-1.5">
                      {Object.entries(img.metadata).map(([key, value]) => (
                        <div key={key} className="flex items-center gap-1 bg-blue-50 text-blue-700 px-2 py-0.5 rounded text-[10px] font-semibold border border-blue-100">
                          <span className="opacity-50 uppercase tracking-tighter">{key}:</span> {value}
                        </div>
                      ))}
                    </div>
                  )}
                </div>
              </div>
            ))}
          </div>
        )}
      </main>
    </div>
  );
};

export default ImageGallery;
