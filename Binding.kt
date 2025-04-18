abstract class Binding {
    @Module
    @InstallIn(SingletonComponent::class)
    abstract class BindModel {

        @Singleton
        @Binds
        abstract fun bindBLEDataSource(impl : BLEDataSourceImpl) : BLEDataSource
    }
}
