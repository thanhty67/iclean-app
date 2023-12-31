package iclean.code.data.enumjava;

import iclean.code.exception.NotFoundException;

public enum ServicePriceEnum {
    FIRST(1, "07:00:00", "12:00:00"),
    SECOND(2, "12:00:00", "18:00:00"),
    THIRD(3, "18:00:00", "21:00:00");
    private final Integer id;
    private final String startDate;
    private final String endDate;
    private ServicePriceEnum(Integer id, String startDate, String endDate) {
        this.id = id;
        this.startDate = startDate;
        this.endDate = endDate;
    }
    public String getStartDate() {
        return startDate;
    }

    public String getEndDate() {
        return endDate;
    }

    public Integer getId() {
        return id;
    }

    public static ServicePriceEnum getById(Integer id) {
        for (ServicePriceEnum serviceEnum : ServicePriceEnum.values()) {
            if (serviceEnum.getId().equals(id)) {
                return serviceEnum;
            }
        }
        throw new NotFoundException("No enum constant with id: " + id);
    }
}
