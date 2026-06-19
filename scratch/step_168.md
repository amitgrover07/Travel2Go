<USER_REQUEST>
Business Logic Specification: Hotel & Transport Allocation System
1. Global Entity & Age Definition Logic

Before running any room or vehicle allocation, the system must parse the guest list array and classify every individual based on age.

    Adult Count (A): Count of individuals where Age >= 12.

    Child With Bed Count (CWB): Count of individuals where Age >= 5 AND Age < 12 AND Bed_Required == True.

    Child No Bed Count (CNB): Count of individuals where Age >= 5 AND Age < 12 AND Bed_Required == False.

    Infant Count (I): Count of individuals where Age < 5 (Automatically Bed_Required == False).

2. Hotel Room Allocation Logic

The system needs to calculate the minimum number of rooms required and the specific Room Configuration Tag for each room to optimize costs.
Max Occupancy Rules Per Standard Room

    Absolute Maximum Capacity: 3 Adults OR 2 Adults + 2 Children.

    Default Base Allocation: 2 Adults per room (Double Sharing).

Sequential Allocation Algorithm

To distribute guests into rooms, apply the following conditional logic loop based on the party makeup:

IF (Total Adults == 1) {
    Assign 1 Room -> Tag: "Single Occupancy"
}
ELSE IF (Total Adults == 2) {
    IF (Children == 0) -> 1 Room -> Tag: "Double Sharing"
    IF (Children == 1 under 5) -> 1 Room -> Tag: "Double Sharing" (Infant Free)
    IF (Children == 1 between 5-11) -> 1 Room -> Tag: "Double + 1 Extra Bed (CWB)" OR "Double + CNB"
    IF (Children == 2) -> 2 Rooms -> Tag: 2 x "Single Occupancy" or 1 "Family Room" (Due to hotel safety laws)
}
ELSE IF (Total Adults == 3) {
    IF (Children == 0) -> Option A: 1 Room -> Tag: "Triple Sharing" (Includes 1 Extra Mattress)
                          Option B: 2 Rooms -> Tag: 1 "Double Sharing" + 1 "Single Occupancy"
}
ELSE IF (Total Adults > 3) {
    Rooms_Needed = Ceil(Total Adults / 2)
    Distribute adults evenly as "Double Sharing". 
    If Total Adults is Odd, the final room becomes a "Triple Sharing" or "Single Occupancy" based 
<truncated 2022 bytes>
 always calculates the total fixed investment and divides it back down to a "Per Adult" cost, while breaking out child costs separately.

Total_Hotel_Cost = Sum of all generated rooms for all nights
Total_Transport_Cost = Fixed vehicle cost for the entire itinerary duration
Total_Fixed_Package_Cost = Total_Hotel_Cost + Total_Transport_Cost + Total_Sightseeing_Tickets

Per_Adult_Package_Price = Total_Fixed_Package_Cost / Total_Adults

Summary of Requirements for Antigravity UI/UX

    Inputs Needed: A dynamic guest addition form collecting: Name, Age, and Bed Preference (Yes/No if age is 5-11).

    Toggle Option: A setting allowing the agent to manually override a "Triple Sharing Room" into "1 Double + 1 Single" if the clients refuse to sleep on an extra floor mattress.

    Vehicle Override: A dropdown allowing the user to upgrade the vehicle (e.g., upgrading 3 Pax from a Sedan to an Innova Crysta for premium luxury), while recalculating the per-head cost in real-time.
</USER_REQUEST>
<ADDITIONAL_METADATA>
The current local time is: 2026-06-19T13:32:07+05:30.

The user's current state is as follows:
Other open documents:
- c:\Users\Amit Grover\OneDrive - IIM Shillong\Desktop\Travel2Go\microservices\package-service\src\main\java\com\travel2go\backend\repository\ConfiguratorCategoryRepository.java (LANGUAGE_JAVA)
- c:\Users\Amit Grover\OneDrive - IIM Shillong\Desktop\Travel2Go\microservices\api-gateway\src\main\resources\application.properties (LANGUAGE_UNSPECIFIED)
- c:\Users\Amit Grover\OneDrive - IIM Shillong\Desktop\Travel2Go\backend\src\main\java\com\travel2go\backend\controller\ConfiguratorCategoryController.java (LANGUAGE_JAVA)
- c:\Users\Amit Grover\OneDrive - IIM Shillong\Desktop\Travel2Go\microservices\package-service\src\main\java\com\travel2go\backend\controller\ConfiguratorCategoryController.java (LANGUAGE_JAVA)
- c:\Users\Amit Grover\OneDrive - IIM Shillong\Desktop\Travel2Go\backend\src\main\java\com\travel2go\backend\model\TravelConfiguration.java (LANGUAGE_JAVA)
</ADDITIONAL_METADATA>