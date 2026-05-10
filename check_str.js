const str = "This is not your average sightseeing tour. Dunes & Skyscrapers is a heart-pounding journey designed for those who want to see Dubai at full throttle. Over the course of this expedition, you will oscillate between the raw power of the desert and the sophisticated heights of the modern world. One moment you’re white-knuckling a 4x4 through red sand dunes; the next, you’re standing on the 148th floor of a glass giant, looking down at the world.";
for(let i=0; i<str.length; i++) {
  if (str.charCodeAt(i) > 255) {
    console.log(str[i], str.charCodeAt(i).toString(16));
  }
}
