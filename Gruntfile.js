module.exports = function(grunt) {
	grunt.initConfig({
		pkg: grunt.file.readJSON('package.json'),
		sass: {
			build: {
				src: 'resources/public/css/style.sass',
				dest: 'resources/public/css/style.css',
			},
		},
		autoprefixer: {
			build: {
				src: 'resources/public/css/style.css',
				dest: 'resources/public/css/style.css',
			},
			options: {
				browsers: ['last 1 version'],
			},
		},
		watch: {
			files: ['resources/**/*.sass'],
			tasks: ['default'],
		},
	});

	grunt.loadNpmTasks('grunt-contrib-sass');
	grunt.loadNpmTasks('grunt-contrib-watch');
	grunt.loadNpmTasks('grunt-autoprefixer');

	grunt.registerTask('default', ['sass', 'autoprefixer']);

};

