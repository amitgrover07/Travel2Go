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

const Home = () => {
  const [packages, setPackages] = useState([]);
  const [loading, setLoading] = useState(true);
  const [activeTab, setActiveTab] = useState('Domestic');
  const userProfile = getTokenPayload();
  const isAdmin = userProfile && userProfile.role === 'ADMIN';

  useEffect(() => {
    const fetchAllData = async () => {
      try {
        setLoading(true);
        console.log('Home Mount - isAdmin:', isAdmin);
        
        const [regRes, custRes] = await Promise.allSettled([
          api.get('/packages'),
          isAdmin ? api.get('/custom-packages/all') : Promise.resolve({ data: [] })
        ]);

        if (regRes.status === 'rejected') {
          console.error('Error fetching regular packages:', regRes.reason);
        }
        if (custRes.status === 'rejected') {
          console.error('Error fetching custom packages:', custRes.reason);
        }

        const regularPkgs = regRes.status === 'fulfilled' ? regRes.value.data : [];
        const customPkgs = (custRes.status === 'fulfilled' && custRes.value && custRes.value.data) ? custRes.value.data : [];
        
        console.log('Fetched Packages - Regular:', regularPkgs.length, 'Custom:', customPkgs.length);
        
        // Ensure custom packages have the correct type for filtering
        const processedCustom = Array.isArray(customPkgs) 
          ? customPkgs.map(p => ({ ...p, packageType: 'Custom' }))
          : [];
        
        setPackages([...regularPkgs, ...processedCustom]);
      } catch (error) {
        console.error('Critical error fetching data:', error);
      } finally {
        setLoading(false);
      }
    };

    fetchAllData();
  }, [isAdmin]);

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
            {['Domestic', 'International', ...(isAdmin ? ['Custom'] : [])].map((tab) => (
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
              <Link
                key={pkg.id}
                to={`/packages/${pkg.id}`}
                className="group flex flex-col bg-white border border-gray-200 rounded-2xl shadow-sm overflow-hidden cursor-pointer"
                style={{
                  transition: 'transform 0.22s ease, box-shadow 0.22s ease, border-color 0.22s ease',
                }}
                onMouseEnter={e => {
                  e.currentTarget.style.transform = 'translateY(-6px)';
                  e.currentTarget.style.boxShadow = '0 16px 40px rgba(59,130,246,0.18)';
                  e.currentTarget.style.borderColor = '#93c5fd';
                }}
                onMouseLeave={e => {
                  e.currentTarget.style.transform = 'translateY(0)';
                  e.currentTarget.style.boxShadow = '0 1px 3px rgba(0,0,0,0.07)';
                  e.currentTarget.style.borderColor = '#e5e7eb';
                }}
              >
                {/* ── Fixed-height image area ── */}
                <div className="relative overflow-hidden" style={{ height: '200px', flexShrink: 0 }}>
                  {/* Package type badge */}
                  <div className="absolute top-3 left-3 z-20">
                    <span className="bg-white/90 backdrop-blur-sm text-blue-600 text-[10px] font-bold px-2.5 py-1 rounded-full shadow">
                      {pkg.packageType || 'Domestic'}
                    </span>
                  </div>

                  {/* Package code badge */}
                  {pkg.packageCode && (
                    <div className="absolute top-3 right-3 z-20">
                      <span className="bg-black/40 backdrop-blur-sm text-white text-[10px] font-bold px-2.5 py-1 rounded-full">
                        {pkg.packageCode}
                      </span>
                    </div>
                  )}

                  {/* Thumbnail with zoom on hover */}
                  <img
                    src={pkg.media?.thumbnailUrl || 'https://images.unsplash.com/photo-1476514525535-07fb3b4ae5f1?ixlib=rb-4.0.3&auto=format&fit=crop&w=800&q=80'}
                    alt={pkg.media?.altText || pkg.title}
                    className="w-full h-full object-cover"
                    style={{ transition: 'transform 0.4s ease' }}
                    onMouseEnter={e => { e.currentTarget.style.transform = 'scale(1.08)'; }}
                    onMouseLeave={e => { e.currentTarget.style.transform = 'scale(1)'; }}
                  />

                  {/* Gradient overlay + CTA that slides up on hover */}
                  <div
                    className="absolute inset-0 flex items-end justify-center pb-4 z-10"
                    style={{
                      background: 'linear-gradient(to top, rgba(30,64,175,0.55) 0%, transparent 60%)',
                      opacity: 0,
                      transition: 'opacity 0.25s ease',
                    }}
                    ref={el => {
                      if (el) {
                        el.closest('a').addEventListener('mouseenter', () => { el.style.opacity = '1'; });
                        el.closest('a').addEventListener('mouseleave', () => { el.style.opacity = '0'; });
                      }
                    }}
                  >
                    <span className="text-white text-xs font-bold tracking-wide px-4 py-1.5 rounded-full border border-white/60 bg-white/10 backdrop-blur-sm">
                      View Package →
                    </span>
                  </div>
                </div>

                {/* ── Fixed-height body ── */}
                <div className="flex flex-col flex-1 p-5">
                  {/* Destination + duration row */}
                  <div className="flex items-center justify-between mb-2">
                    <div className="flex items-center text-xs text-gray-500 min-w-0">
                      <MapPin className="h-3.5 w-3.5 mr-1 text-blue-400 flex-shrink-0" />
                      <span className="truncate">{pkg.destination}</span>
                    </div>
                    <div className="flex items-center text-xs text-gray-400 flex-shrink-0 ml-2">
                      <Clock className="h-3.5 w-3.5 mr-1" />
                      {pkg.duration?.days}D / {pkg.duration?.nights}N
                    </div>
                  </div>

                  {/* Title — 2 lines max, then ellipsis */}
                  <h3
                    className="text-base font-bold text-gray-900 leading-snug mb-2"
                    style={{
                      display: '-webkit-box',
                      WebkitLineClamp: 2,
                      WebkitBoxOrient: 'vertical',
                      overflow: 'hidden',
                      minHeight: '2.6em',
                      transition: 'color 0.2s ease',
                    }}
                    ref={el => {
                      if (el) {
                        el.closest('a').addEventListener('mouseenter', () => { el.style.color = '#2563eb'; });
                        el.closest('a').addEventListener('mouseleave', () => { el.style.color = '#111827'; });
                      }
                    }}
                  >
                    {pkg.title}
                  </h3>

                  {/* Overview — 3 lines max, reserved block so cards stay aligned */}
                  <p
                    className="text-gray-500 text-xs leading-relaxed"
                    style={{
                      display: '-webkit-box',
                      WebkitLineClamp: 3,
                      WebkitBoxOrient: 'vertical',
                      overflow: 'hidden',
                      minHeight: '4.5em',   /* 3 lines × 1.5em line-height */
                      flex: '0 0 auto',
                    }}
                  >
                    {stripHtml(pkg.overview)}
                  </p>

                  {/* ── Pinned footer ── */}
                  <div className="mt-auto pt-4 border-t border-gray-100">
                    <div className="flex items-end justify-between">
                      <div>
                        <span className="text-lg font-extrabold text-blue-600">
                          {pkg.pricing?.currency || 'INR'} {formatCurrency(pkg.pricing?.finalPrice)}
                        </span>
                        <div className="text-[10px] text-gray-400 italic mt-0.5">
                          {numberToWords(pkg.pricing?.finalPrice)}
                        </div>
                      </div>
                      <span
                        className="text-xs font-semibold text-blue-600 border border-blue-200 rounded-lg px-3 py-1.5 bg-blue-50"
                        style={{ transition: 'background 0.18s ease, color 0.18s ease' }}
                        ref={el => {
                          if (el) {
                            el.closest('a').addEventListener('mouseenter', () => {
                              el.style.background = '#2563eb';
                              el.style.color = '#fff';
                            });
                            el.closest('a').addEventListener('mouseleave', () => {
                              el.style.background = '#eff6ff';
                              el.style.color = '#2563eb';
                            });
                          }
                        }}
                      >
                        Explore →
                      </span>
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
