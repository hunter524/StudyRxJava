package huntertest;

public class LetCode {
    public static void main(String[] args) {
        int[] origin = {1,3,5,6,9,10};
        int test1 = 9;
        int test2 = 8;
        int result1 = searchInsert(origin, test1);
        int result2 = searchInsert(origin, test2);
        System.out.println("result1:"+result1+"\nresult2:"+result2);
    }

    public static int searchInsert(int[] nums, int target) {
        int left = 0, right = nums.length - 1, mid = (right + left) >> 1;
        while (left <= right) {
            if (target <= nums[mid]) right = mid - 1;
            else left = mid + 1;
            mid = (right + left) >> 1;
        }
        return left;
    }
}
