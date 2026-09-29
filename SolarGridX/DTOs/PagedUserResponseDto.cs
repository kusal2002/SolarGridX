namespace SolarGridX.DTOs
{
    public class PagedUserResponseDto
    {
        public IReadOnlyList<UserResponseDto> Items { get; set; } = [];

        public int Page { get; set; }

        public int PageSize { get; set; }

        public long TotalCount { get; set; }

        public int TotalPages { get; set; }
    }
}