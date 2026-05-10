const { DOMParser } = require('xmldom');

const stripHtml = (html) => {
  if (!html) return "";
  // First clean any &nbsp; or literal non-breaking spaces
  const cleaned = html.replace(/&nbsp;/gi, ' ').replace(/\u00A0/g, ' ');
  
  // Basic mock for testing
  return cleaned.replace(/<[^>]*>?/gm, '');
};

const html = "This&nbsp;is&nbsp;a\u00A0test";
const stripped = stripHtml(html);
console.log(stripped);
console.log(stripped.charCodeAt(4)); // should be 32 (space)
