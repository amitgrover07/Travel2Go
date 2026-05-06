import React, { useState, useEffect } from 'react';
import { useParams, Link } from 'react-router-dom';
import { MapPin, Clock, DollarSign, ChevronLeft, ChevronRight, CheckCircle2, XCircle, Calendar, ArrowLeft } from 'lucide-react';
import api from '../services/api';

const renderBulletPoints = (text) => {
  if (!text) return null;
  const points = text.split('\n').filter(p => p.trim() !== '');
  if (points.length <= 1 && !text.includes('\n')) return text;
  
  return (
    <ul className="list-disc ml-5 space-y-2 mt-2">
      {points.map((point, index) => (
        <li key={index} className="text-gray-700 leading-relaxed">{point}</li>
      ))}
    </ul>
  );
};

const PackageDetails = () => {
  const { id } = useParams();
  const [pkg, setPkg] = useState(null);
  const [loading, setLoading] = useState(true);
  const [currentImageIndex, setCurrentImageIndex] = useState(0);

  useEffect(() => {
    const fetchPackageDetails = async () => {
      try {
        const response = await api.get(`/packages/${id}`);
        setPkg(response.data);
      } catch (error) {
        console.error('Error fetching package details:', error);
      } finally {
        setLoading(false);
      }
    };

    fetchPackageDetails();
  }, [id]);

  // Combine thumbnail and gallery urls for the carousel
  const images = [];
  if (pkg?.media?.thumbnailUrl && pkg.media.thumbnailUrl.trim() !== '') {
    images.push(pkg.media.thumbnailUrl);
  }
  if (pkg?.media?.galleryUrls && Array.isArray(pkg.media.galleryUrls)) {
    pkg.media.galleryUrls.forEach(url => {
      if (url && url.trim() !== '' && !images.includes(url)) {
        images.push(url);
      }
    });
  }

  // Fallback image if empty
  if (images.length === 0) {
    images.push('https://images.unsplash.com/photo-1476514525535-07fb3b4ae5f1?ixlib=rb-4.0.3&auto=format&fit=crop&w=1600&q=80');
  }

  useEffect(() => {
    if (images.length <= 1) return;
    
    const interval = setInterval(() => {
      setCurrentImageIndex((prev) => (prev + 1) % images.length);
    }, 5000);

    return () => clearInterval(interval);
  }, [images.length]);

  if (loading) {
    return <div className="min-h-screen flex items-center justify-center">Loading...</div>;
  }

  if (!pkg) {
    return <div className="min-h-screen flex items-center justify-center flex-col">
      <h2 className="text-2xl font-bold mb-4">Package Not Found</h2>
      <Link to="/" className="text-blue-600 hover:underline flex items-center">
        <ArrowLeft className="w-4 h-4 mr-1" /> Back to Home
      </Link>
    </div>;
  }

  const nextImage = () => setCurrentImageIndex((prev) => (prev + 1) % images.length);
  const prevImage = () => setCurrentImageIndex((prev) => (prev - 1 + images.length) % images.length);

  return (
    <div className="min-h-screen bg-gray-50 pb-12">

      <main className="max-w-7xl mx-auto px-0 sm:px-6 lg:px-8 mt-0 sm:mt-8">
        <div className="bg-white shadow-none sm:shadow-lg sm:rounded-2xl overflow-hidden">
          {/* Image Carousel */}
          <div className="relative aspect-w-16 aspect-h-12 sm:aspect-h-7 lg:aspect-h-6 bg-gray-200">
            <img
              src={images[currentImageIndex]}
              alt={`Slide ${currentImageIndex + 1}`}
              className="object-cover w-full h-[40vh] sm:h-[60vh] transition-opacity duration-500"
            />
            
            {/* Carousel Controls */}
            {images.length > 1 && (
              <>
                <button
                  onClick={prevImage}
                  className="absolute left-2 sm:left-4 top-1/2 -translate-y-1/2 bg-black/30 hover:bg-black/50 text-white p-2 rounded-full backdrop-blur-sm transition-all"
                >
                  <ChevronLeft className="h-6 w-6" />
                </button>
                <button
                  onClick={nextImage}
                  className="absolute right-2 sm:right-4 top-1/2 -translate-y-1/2 bg-black/30 hover:bg-black/50 text-white p-2 rounded-full backdrop-blur-sm transition-all"
                >
                  <ChevronRight className="h-6 w-6" />
                </button>
                {/* Dots indicator */}
                <div className="absolute bottom-4 left-1/2 -translate-x-1/2 flex space-x-2">
                  {images.map((_, idx) => (
                    <button
                      key={idx}
                      onClick={() => setCurrentImageIndex(idx)}
                      className={`w-2 h-2 rounded-full transition-all ${currentImageIndex === idx ? 'bg-white w-4' : 'bg-white/50'}`}
                    />
                  ))}
                </div>
              </>
            )}
          </div>

          {/* Content */}
          <div className="p-4 sm:p-8">
            <div className="flex flex-col lg:flex-row lg:justify-between lg:items-start mb-8 gap-6">
              <div className="flex-1">
                <div className="flex items-center gap-3 text-sm text-blue-600 font-medium mb-3">
                  <span className="flex items-center">
                    <MapPin className="h-4 w-4 mr-1" />
                    {pkg.destination}
                  </span>
                  {pkg.packageCode && (
                    <span className="bg-blue-50 text-blue-700 px-2.5 py-0.5 rounded-md text-xs border border-blue-100">
                      {pkg.packageCode}
                    </span>
                  )}
                </div>
                <h1 className="text-3xl sm:text-4xl font-extrabold text-gray-900 leading-tight mb-4">
                  {pkg.title}
                </h1>
                <div 
                  className="text-gray-600 text-base sm:text-lg leading-relaxed quill-content"
                  dangerouslySetInnerHTML={{ __html: pkg.overview }}
                />
              </div>

              {/* Pricing Card */}
              <div className="bg-blue-50/50 border border-blue-100 rounded-xl p-6 lg:w-80 flex-shrink-0">
                <div className="flex items-center text-gray-600 mb-4">
                  <Clock className="h-5 w-5 mr-2 text-blue-500" />
                  <span className="font-medium text-lg">{pkg.duration?.days} Days / {pkg.duration?.nights} Nights</span>
                </div>
                <div className="mb-2">
                  <span className="text-gray-500 text-sm font-medium uppercase tracking-wide">Starting from</span>
                </div>
                <div className="flex items-baseline mb-6">
                  <span className="text-4xl font-extrabold text-gray-900 tracking-tight">
                    {pkg.pricing?.currency || 'INR'} {pkg.pricing?.finalPrice}
                  </span>
                  {pkg.pricing?.basePrice > pkg.pricing?.finalPrice && (
                    <span className="ml-3 text-lg text-gray-400 line-through decoration-red-400">
                      {pkg.pricing?.currency || 'INR'} {pkg.pricing?.basePrice}
                    </span>
                  )}
                </div>
                <button className="w-full bg-blue-600 hover:bg-blue-700 text-white font-semibold py-3.5 px-4 rounded-lg shadow-sm hover:shadow-md transition-all">
                  Book Now
                </button>
              </div>
            </div>

            <div className="grid grid-cols-1 md:grid-cols-2 gap-8 mb-12">
              {/* Inclusions */}
              {pkg.inclusions && pkg.inclusions.length > 0 && (
                <div className="bg-gray-50 rounded-xl p-6 border border-gray-100">
                  <h3 className="text-xl font-bold text-gray-900 mb-4 flex items-center">
                    <CheckCircle2 className="h-6 w-6 text-green-500 mr-2" />
                    What's Included
                  </h3>
                  <ul className="space-y-3">
                    {pkg.inclusions.map((item, idx) => (
                      <li key={idx} className="flex items-start">
                        <span className="text-green-500 mr-2 mt-1">•</span>
                        <span className="text-gray-700">{item}</span>
                      </li>
                    ))}
                  </ul>
                </div>
              )}

              {/* Exclusions */}
              {pkg.exclusions && pkg.exclusions.length > 0 && (
                <div className="bg-gray-50 rounded-xl p-6 border border-gray-100">
                  <h3 className="text-xl font-bold text-gray-900 mb-4 flex items-center">
                    <XCircle className="h-6 w-6 text-red-500 mr-2" />
                    What's Excluded
                  </h3>
                  <ul className="space-y-3">
                    {pkg.exclusions.map((item, idx) => (
                      <li key={idx} className="flex items-start">
                        <span className="text-red-400 mr-2 mt-1">•</span>
                        <span className="text-gray-700">{item}</span>
                      </li>
                    ))}
                  </ul>
                </div>
              )}
            </div>

            {/* Itinerary */}
            {pkg.itinerary && pkg.itinerary.length > 0 && (
              <div className="border-t border-gray-200 pt-10">
                <h2 className="text-2xl font-bold text-gray-900 mb-8 flex items-center">
                  <Calendar className="h-7 w-7 text-blue-600 mr-3" />
                  Detailed Itinerary
                </h2>
                <div className="space-y-8 relative before:absolute before:inset-0 before:ml-5 before:-translate-x-px md:before:mx-auto md:before:translate-x-0 before:h-full before:w-0.5 before:bg-gradient-to-b before:from-transparent before:via-gray-200 before:to-transparent">
                  {pkg.itinerary.map((day, idx) => (
                    <div key={idx} className="relative flex items-start md:justify-center">
                      <div className="absolute left-0 md:left-1/2 -ml-3 md:-ml-3 mt-1.5 h-6 w-6 rounded-full border-4 border-white bg-blue-500 shadow-sm z-10 flex items-center justify-center"></div>
                      <div className="ml-10 md:ml-0 md:w-1/2 md:even:pl-10 md:odd:pr-10 md:odd:text-right flex flex-col w-full">
                        <div className="bg-white p-5 rounded-xl border border-gray-100 shadow-sm hover:shadow-md transition-shadow">
                          <div className="flex items-center md:odd:justify-end gap-2 mb-2">
                            <span className="bg-blue-100 text-blue-800 text-xs font-bold px-2.5 py-1 rounded">Day {day.day}</span>
                            <h4 className="text-lg font-bold text-gray-900">{day.title}</h4>
                          </div>
                          <div 
                            className="text-gray-600 text-sm md:text-base leading-relaxed quill-content"
                            dangerouslySetInnerHTML={{ __html: day.activities }}
                          />
                        </div>
                      </div>
                    </div>
                  ))}
                </div>
              </div>
            )}

            {/* Special Notes / Conditions */}
            {pkg.specialNotes && pkg.specialNotes !== '<p><br></p>' && pkg.specialNotes !== '' && (
              <div id="special-notes" className="mt-12 bg-red-50/50 border-2 border-red-100 rounded-2xl p-8 shadow-sm">
                <h3 className="text-2xl font-bold text-red-600 mb-6 flex items-center">
                  <span className="bg-red-600 text-white p-1 rounded mr-3 flex items-center justify-center">
                    <CheckCircle2 size={18} />
                  </span>
                  Important Notes & Conditions
                </h3>
                <div 
                  className="text-gray-800 text-lg leading-relaxed quill-content prose max-w-none"
                  dangerouslySetInnerHTML={{ __html: pkg.specialNotes }}
                />
              </div>
            )}
          </div>
        </div>
      </main>
    </div>
  );
};

export default PackageDetails;
