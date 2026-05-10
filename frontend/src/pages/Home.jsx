import React, { useState, useEffect } from 'react';
import { Link } from 'react-router-dom';
import { MapPin, Clock } from 'lucide-react';
import api from '../services/api';
import { formatCurrency, numberToWords } from '../utils/formatUtils';

const stripHtml = (html) => {
  if (!html) return "";
  // First clean any &nbsp; or literal non-breaking/zero-width spaces
  const cleaned = html.replace(/&nbsp;/gi, ' ').replace(/[\u00A0\u200B-\u200D\uFEFF\u202F]/g, ' ');
  const doc = new DOMParser().parseFromString(cleaned, 'text/html');
  return doc.body.textContent || "";
};

const Home = () => {
  const [packages, setPackages] = useState([]);
  const [loading, setLoading] = useState(true);
  const [activeTab, setActiveTab] = useState('Domestic');

  useEffect(() => {
    const fetchPackages = async () => {
      try {
        const response = await api.get('/packages');
        setPackages(response.data);
      } catch (error) {
        console.error('Error fetching packages:', error);
      } finally {
        setLoading(false);
      }
    };

    fetchPackages();
  }, []);

  const filteredPackages = packages.filter(pkg => {
    // Fallback for old packages that might not have the type field yet
    const type = pkg.packageType || 'Domestic';
    return type === activeTab;
  });

  if (loading) {
    return <div className="min-h-screen flex items-center justify-center">Loading...</div>;
  }

  return (
    <div className="bg-gray-50 flex-grow">
      <main className="max-w-7xl mx-auto py-12 px-4 sm:px-6 lg:px-8 overflow-hidden">
        <div className="text-center mb-16">
          <h1 className="text-4xl tracking-tight font-extrabold text-gray-900 sm:text-5xl md:text-6xl">
            <span className="block">Explore the World</span>
            <span className="block text-blue-600">With Premium Packages</span>
          </h1>
          <p className="mt-3 max-w-md mx-auto text-base text-gray-500 sm:text-lg md:mt-5 md:text-xl md:max-w-3xl">
            Discover our handpicked holiday packages for your next unforgettable adventure.
          </p>
        </div>

        {/* Tabs */}
        <div className="flex justify-center mb-10 w-full overflow-x-auto pb-2">
          <div className="inline-flex p-1 bg-gray-200 rounded-xl shadow-inner min-w-max">
            {['Domestic', 'International'].map((tab) => (
              <button
                key={tab}
                onClick={() => setActiveTab(tab)}
                className={`px-4 sm:px-8 py-3 rounded-lg text-sm font-bold transition-all duration-300 ${
                  activeTab === tab
                    ? 'bg-white text-blue-600 shadow-md transform scale-105'
                    : 'text-gray-500 hover:text-gray-700'
                }`}
              >
                {tab} Packages
              </button>
            ))}
          </div>
        </div>

        <div className="grid grid-cols-1 gap-y-10 gap-x-6 sm:grid-cols-2 lg:grid-cols-3 xl:gap-x-8">
          {filteredPackages.length > 0 ? (
            filteredPackages.map((pkg) => (
              <Link key={pkg.id} to={`/packages/${pkg.id}`} className="group relative bg-white border border-gray-200 rounded-2xl shadow-sm hover:shadow-lg transition-shadow duration-300 flex flex-col cursor-pointer block max-w-full">
                <div className="aspect-w-3 aspect-h-2 bg-gray-200 relative overflow-hidden rounded-t-2xl">
                  <div className="absolute top-4 left-4 z-10">
                    <span className="bg-white/90 backdrop-blur text-blue-600 text-[10px] font-bold px-2 py-1 rounded-full shadow-sm">
                      {pkg.packageType || 'Domestic'}
                    </span>
                  </div>
                  <img
                    src={pkg.media?.thumbnailUrl || 'https://images.unsplash.com/photo-1476514525535-07fb3b4ae5f1?ixlib=rb-4.0.3&auto=format&fit=crop&w=800&q=80'}
                    alt={pkg.media?.altText || pkg.title}
                    className="object-cover w-full h-48"
                  />
                </div>
                <div className="p-6 flex-1 flex flex-col items-center text-center overflow-hidden">
                  <div className="flex flex-wrap items-center justify-center gap-2 mb-2 w-full">
                    <div className="flex items-center text-sm text-gray-500 shrink-0">
                      <MapPin className="h-4 w-4 mr-1 text-blue-500" />
                      {pkg.destination}
                    </div>
                    {pkg.packageCode && <span className="text-xs bg-blue-100 text-blue-800 px-2 py-1 rounded-full shrink-0">{pkg.packageCode}</span>}
                  </div>
                  <h3 className="text-xl font-bold text-gray-900 mb-2 leading-tight max-w-full">{pkg.title}</h3>
                  <p className="text-gray-600 text-sm mb-4 line-clamp-3 flex-1">{stripHtml(pkg.overview)}</p>
                  
                  <div className="mt-auto border-t border-gray-100 pt-4 flex flex-col w-full">
                    <div className="flex flex-wrap items-center justify-center sm:justify-between gap-2 mb-1">
                      <div className="flex items-center text-gray-700 font-medium">
                        <span className="text-xl font-bold text-blue-600 shrink-0">
                          {pkg.pricing?.currency || 'INR'} {formatCurrency(pkg.pricing?.finalPrice)}
                        </span>
                      </div>
                      <div className="flex items-center text-gray-500 text-sm shrink-0">
                        <Clock className="h-4 w-4 mr-1" />
                        {pkg.duration?.days}D / {pkg.duration?.nights}N
                      </div>
                    </div>
                    <div className="text-[10px] text-gray-400 font-medium italic text-center sm:text-left">
                      {numberToWords(pkg.pricing?.finalPrice)}
                    </div>
                  </div>
                </div>
              </Link>
            ))
          ) : (
            <div className="col-span-full text-center py-20 bg-white rounded-2xl border-2 border-dashed border-gray-200">
              <p className="text-gray-500 font-medium">No {activeTab} packages found. Check back later!</p>
            </div>
          )}
        </div>
      </main>
    </div>
  );
};

export default Home;
